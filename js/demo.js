"use strict";

var submitBtn = $("submit");
var code = document.getElementById("code");
var outputArea = $("output-area");

function onSubmit(_) {
  submitBtn.prop("disabled", true);

  var data = { manifest: $("code").val(), os: "ubuntu-trusty" }

  $.ajax({
    url: "http://104.196.37.39:5000/rehearsal",
    method: "POST",
    processData: false,
    data: JSON.stringify(data),
    complete: function() { submitBtn.prop("disabled", false); },
    success: function(reply) {
      outputArea.text(reply);
    },
    error: function(_, text, _) {
      outputArea.text(text);
    }
  });
}

document.getElementById("submit").addEventListener("click", onSubmit);

submitBtn.click(onSubmit);
