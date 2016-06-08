"use strict";

var editor = ace.edit("code");
editor.setTheme("ace/theme/monokai");
editor.setOptions({
    minLines: 10,
    maxLines: 25
});

var submitBtn = $("#submit");
var outputArea = $("#output");

function onSubmit(_) {
  submitBtn.prop("disabled", true);
  outputArea.hide();
  var data = { manifest: editor.getValue(), os: $("#os").val() }

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
  { label: "Apache", url: "../examples/apache.pp" },
  { label: "Accounts", url: "../examples/accounts.pp" },
  { label: "Hosts", url: "../examples/hosts.pp" },
  { label: "Delete Source", url: "../examples/idem.pp" }
];

var examplesDiv = $("#examples");

function showExample(arg) {
  var elt = $("<a href=\"#\"></a>").text(arg.label);
  $(elt).click(function() {
    outputArea.hide();
    $.ajax({
      url: arg.url,
      success: function(reply) {
        editor.setValue(reply, 0);
      }
    });
  });
  examplesDiv.append(elt);
  examplesDiv.append($("<br>"));
}

for (var i = 0; i < examples.length; i++) {
  showExample(examples[i]);
}