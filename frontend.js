"use strict";

function onSubmit(_) {
  var xhr = new XMLHttpRequest();
  xhr.open("POST", "http://104.196.37.39:5000/rehearsal", true)
  xhr.send();
}

document.getElementById("submit").addEventListener("click", onSubmit);