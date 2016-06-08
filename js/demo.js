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

var examples = [
  { label: "Missing dependency", url: "../examples.apache.pp" }
];

var examplesDiv = $("#examples");


function showExample(arg) {
  console.log(arg);
  var elt = $("<a href=\"#\"></a>").text(arg.label);
  $(elt).click(function() {
    $.ajax({
      url: arg.url,
      success: function(reply) {
        $("#code").val(reply);
      }
    });
  });
}

$(examples).each(showExample);