
var song = document.getElementById("song");

var fontSize = 100;
var biggerButton = document.getElementById("biggerButton");
biggerButton.addEventListener("click", (e) => {
    fontSize += 10;
    song.style.fontSize = fontSize + "%";
});
var smallerButton = document.getElementById("smallerButton");
smallerButton.addEventListener("click", (e) => {
    fontSize -= 10;
    song.style.fontSize = fontSize + "%";
});

// FullScreen
var fullScreenButton = document.getElementById("fullScreenButton");
fullScreenButton.addEventListener("click", (e) => {
    if (isFullScreen()) {
        exitFullScreen();
        (<HTMLElement>fullScreenButton.firstElementChild).classList.remove("active");
    } else {
        requestFullScreen(document.body);
        (<HTMLElement>fullScreenButton.firstElementChild).classList.add("active");
    }
});

function isFullScreen(): boolean {
    if (document["isFullScreen"]) {
        return document["isFullScreen"]
    } else if (document["webkitIsFullScreen"]) {
        return document["webkitIsFullScreen"]
    } else if (document["mozIsFullScreen"]) {
        return document["mozIsFullScreen"]
    }
}
function exitFullScreen(): boolean {
    if (document["exitFullscreen"]) {
        return document["exitFullscreen"]()
    } else if (document["webkitExitFullscreen"]) {
        return document["webkitExitFullscreen"]()
    } else if (document["mozCancelFullScreen"]) {
        return document["mozCancelFullScreen"]()
    }
}

function requestFullScreen(element: HTMLElement) {
    if (element["requestFullScreen"]) {
        element["requestFullScreen"]()
    } else if (element["webkitRequestFullScreen"]) {
        element["webkitRequestFullScreen"]()
    } else if (element["mozRequestFullScreen"]) {
        element["mozRequestFullScreen"]()
    }
}

// Restore two column current user pref
var twoColumnButton = document.getElementById("twoColumnButton");
if (localStorage.getItem("song-column") === "true") {
    song.classList.add("song-column");
    (<HTMLElement>twoColumnButton.firstElementChild).classList.add("active");
} else {
    song.classList.remove("song-column");
    twoColumnButton.classList.remove("active");
    (<HTMLElement>twoColumnButton.firstElementChild).classList.remove("active");
}

twoColumnButton.addEventListener("click", (e) => {
    var songColumn = song.classList.toggle("song-column");
    (<HTMLElement>twoColumnButton.firstElementChild).classList.toggle("active");
    localStorage.setItem("song-column", songColumn + "");
});

// Transposition
var transposeCount = 0;
var musicalKey = null
var transposeDisplay = document.getElementById("transposeDisplay");
var musicalKeyElt = song.querySelector(".song-metadata-value[itemprop=musicalKey]");
if (musicalKeyElt) {
    musicalKey = musicalKeyElt.textContent;
    transposeDisplay.innerHTML = musicalKey
} else {
    transposeDisplay.innerHTML = "0";
}

var forEachNode = function (list: NodeList, callback: (node: Node)=>void, context?: any){
    return Array.prototype.forEach.call(list, callback, context);
};

var transposeLessButton = document.getElementById("transposeLessButton");
transposeLessButton.addEventListener("click", (e) => {
    transposeAll(-1);
});

var transposeMoreButton = document.getElementById("transposeMoreButton");
transposeMoreButton.addEventListener("click", (e) => {
    transposeAll(1);
});

function transposeAll(count: number) {
    transposeCount+=count;
    forEachNode(song.querySelectorAll(".song-chord"), (chordElt: HTMLElement) => {
        chordElt.textContent = transpose(chordElt.textContent, count);
    });
    if (musicalKey) {
        transposeDisplay.innerHTML = transpose(musicalKey, transposeCount);
    } else {
        transposeDisplay.innerHTML = transposeCount + "";
    }
}


var notesIndexes = {
    "C" : 0,
    "C#": 1,
    "Db": 1,
    "D" : 2,
    "D#": 3,
    "Eb": 3,
    "E" : 4,
    "F" : 5,
    "F#": 6,
    "Gb": 6,
    "G" : 7,
    "G#": 8,
    "Ab": 8,
    "A" : 9,
    "A#": 10,
    "Bb": 10,
    "B" : 11
}

var notes = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];

function transpose(chord: string, demiToneCount: number): string {
    var note = chord[0];
    if (chord.length > 1 && (chord[1] === "b" || chord[1] === "#")) {
        note += chord[1];
    }

    var newNote = notes[getNoteIndex(notesIndexes[note]+demiToneCount)];
    var indexOfBass = chord.indexOf("/");
    if (indexOfBass !== -1 && indexOfBass < chord.length - 1) {
        chord = chord.substring(0, indexOfBass+1) + transpose(chord.substring(indexOfBass+1), demiToneCount);
    }
    return newNote + chord.substring(note.length);
}

function getNoteIndex(i: number): number {
    if (i < 0) {
        return getNoteIndex(12 + i);
    }
    if (i >= 12) {
        return getNoteIndex(i - 12);
    }
    return i;
}

