"use strict";

var submitBtn = $("#submit");
var outputArea = $("#output");

function onSubmit(_) {
  submitBtn.prop("disabled", true);

  var data = { manifest: $("#code").val(), os: "ubuntu-trusty" }

  $.ajax({
    url: "http://104.196.37.39:5000/rehearsal",
    method: "POST",
    processData: false,
    data: JSON.stringify(data),
    complete: function() { submitBtn.prop("disabled", false); },
    success: function(reply) {
      outputArea.show();
      outputArea.text(reply);
    },
    error: function(_, text) {
      outputArea.show();
      outputArea.text(text);
    }
  });
}

document.getElementById("submit").addEventListener("click", onSubmit);

submitBtn.click(onSubmit);
