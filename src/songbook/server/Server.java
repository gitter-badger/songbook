package songbook.server;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.*;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.shareddata.ConcurrentSharedMap;
import org.vertx.java.platform.Verticle;
import songbook.index.HtmlIndexer;
import songbook.index.IndexDatabase;
import songbook.index.SongUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Server extends Verticle {

    public final static int DEFAULT_PORT = 8080;

    public final static String DEFAULT_HOST = "localhost";

    public final static String DEFAULT_WEB_ROOT = "./web";

    public final static String DEFAULT_DATA_ROOT = "data";

    private Path webRoot;

    private Path dataRoot;

    private IndexDatabase indexDatabase;

    @Override
    public void start() {
        try {
            webRoot = getWebRoot();

            dataRoot = getDataRoot();

            indexDatabase = new IndexDatabase(dataRoot.resolve("index"));

            indexDatabase.analyzeSongs(dataRoot.resolve("songs"));

            HttpServer httpServer = vertx.createHttpServer();
            RouteMatcher routeMatcher = new RouteMatcher();
            Logger log = container.logger();

            Handler<HttpServerRequest> searchHandler = (req) -> {
                // Serve all songs
                HttpServerResponse response = req.response();
                response.putHeader(HttpHeaders.CONTENT_TYPE, "text/html");

                String query = req.params().get("query");
                response.setChunked(true);
                String title = "My SongBook";
                if (query != null && !query.isEmpty()) {
                    title = query + " - " + title;
                }
                response.write(Templates.getHeader(title));
                response.write(Templates.getNavigation());
                try {
                    indexDatabase.search(query, req);
                } catch (ParseException e) {
                    e.printStackTrace();
                    response.write("Wrong Query Syntax");
                } catch (IOException e) {
                    e.printStackTrace();
                    response.write("Internal Error");
                }
                response.write(Templates.getFooter(null));
                response.end();
            };
            routeMatcher.get("/search/:query", searchHandler);
            routeMatcher.get("/search", searchHandler);
            routeMatcher.get("/", searchHandler);

            routeMatcher.get("/songs/:id", (req) -> {
                // Serve song
                HttpServerResponse response = req.response();
                response.putHeader(HttpHeaders.CONTENT_TYPE, "text/html");

                String id = QueryStringDecoder.decodeComponent(req.params().get("id"));
                response.setChunked(true);
                response.write(Templates.getHeader(id + " - My SongBook"));
                response.write(Templates.getNavigation());
                readHtmlSong(id, (e) -> {
                    if (e.succeeded()) {
                        response.write(e.result());
                        log.trace("Serve Song " + id);
                    } else {
                        response.write("Error loading song " + id);
                        log.error("Failed to read song " + id, e.cause());
                        response.setStatusCode(404);
                    }
                    response.write(Templates.getFooter(null));
                    response.end();
                });
            });

            routeMatcher.post("/songs", (req) -> {
                HttpServerResponse response = req.response();
                req.bodyHandler((body) -> {
                    String songData = body.toString();
                    Document document;
                    try {
                        HtmlIndexer songIndexer = new HtmlIndexer();
                        document = songIndexer.indexSong(songData);

                        Path filePath = Files.createTempFile(dataRoot.resolve("songs"), "", ".html").toAbsolutePath();
                        String id = SongUtil.getId(filePath.getFileName().toString());
                        document.add(new StringField("id", id, Field.Store.YES));
                        vertx.fileSystem().writeFile(filePath.toString(), body, (ar) -> {
                            if (ar.succeeded()) {
                                response.end(id);
                            } else {
                                log.error("Failed to create song", ar.cause());
                                response.setStatusCode(500);
                                response.end();
                                try {
                                    Files.deleteIfExists(filePath);
                                } catch (IOException e) {
                                    log.warn("Can't delete file", e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        log.error("Error indexing song", e);
                        response.setStatusCode(500);
                        response.end();
                    }
                });
            });

            routeMatcher.put("/songs/:id", (req) -> {
                HttpServerResponse response = req.response();
                String id = req.params().get("id");
                Path filePath = dataRoot.resolve("songs").resolve(id).toAbsolutePath();
                req.bodyHandler((body) -> {
                    String songData = body.toString();
                    Document document;
                    try {
                        HtmlIndexer songIndexer = new HtmlIndexer();
                        document = songIndexer.indexSong(songData);
                        document.add(new StringField("id", id, Field.Store.YES));

                        vertx.fileSystem().writeFile(filePath.toString(), body, (ar) -> {
                            if (ar.succeeded()) {
                                response.end(id);
                            } else {
                                log.error("Failed to create song", ar.cause());
                                response.setStatusCode(500);
                                response.end();
                                try {
                                    Files.deleteIfExists(filePath);
                                } catch (IOException e) {
                                    log.warn("Can't delete file", e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        log.error("Error indexing song", e);
                        response.setStatusCode(500);
                        response.end();
                    }
                });
            });

            routeMatcher.noMatch((req) -> {
                // Serve Files
                HttpServerResponse response = req.response();
                String path = req.path();
                String fileName = path.equals("/") ? "index.html" : path;
                Path localFilePath = Paths.get(webRoot.toString(), QueryStringDecoder.decodeComponent(fileName)).toAbsolutePath();
                log.info("GET " + localFilePath);
                String type = "text/plain";
                if (fileName.endsWith(".js")) {
                    type = "application/javascript";
                } else if (fileName.endsWith(".css")) {
                    type = "text/css";
                } else if (fileName.endsWith(".html")) {
                    type = "text/html";
                }

                response.putHeader(HttpHeaders.CONTENT_TYPE, type);
                response.sendFile(localFilePath.toString());
            });


            httpServer.requestHandler(routeMatcher);
            httpServer.listen(getPort(), getHost());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readHtmlSong(String id, Handler<AsyncResult<String>> handler) {
        DefaultFutureResult<String> event = new DefaultFutureResult<>();
        event.setHandler(handler);
        try {
            ConcurrentSharedMap<Object, String> songs = vertx.sharedData().getMap("songs");
            String song = songs.get(id);
            if (song != null) {
                event.setResult(song);
            } else {
                final Path songPath = dataRoot.resolve("songs").resolve(id + IndexDatabase.SONG_EXTENSION).toAbsolutePath();
                vertx.fileSystem().readFile(songPath.toString(), (e) -> {
                    if (e.succeeded()) {
                        String songFromFile = e.result().toString();
                        songs.put(id, songFromFile);
                        event.setResult(songFromFile);
                    } else {
                        event.setFailure(e.cause());
                    }
                });

            }
        } catch (Throwable e) {
            event.setFailure(e);
        }
    }

    private Path getWebRoot() {
        final String webRoot = System.getenv("WEB_ROOT");
        return Paths.get(webRoot == null ? DEFAULT_WEB_ROOT : webRoot);
    }

    private static Path getDataRoot() {
        final String dataRoot = System.getenv("DATA_ROOT");
        return Paths.get(dataRoot == null ? DEFAULT_DATA_ROOT : dataRoot);
    }

    private int getPort() {
        final String portString = System.getenv("PORT");
        int port = DEFAULT_PORT;
        if ( portString != null ) {
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                // doesn't matter;
            }
        }
        return port;
    }

    private String getHost() {
        final String host = System.getenv("HOST");
        return host == null ? DEFAULT_HOST : host;
    }
}
