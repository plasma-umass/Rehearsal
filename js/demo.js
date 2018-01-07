"use strict";

var editor = ace.edit("code");
editor.setTheme("ace/theme/monokai");
editor.setOptions({
    minLines: 20,
    maxLines: 60
});

var submitBtn = $("#submit");
var outputArea = $("#output");

function onSubmit(_) {
  submitBtn.prop("disabled", true);
  outputArea.hide();
  var data = { manifest: editor.getValue(), os: $("#os").val(), pred: $("#pred").val() }

  $.ajax({
    url: "https://openwhisk.ng.bluemix.net/api/v1/web/PLASMA_dev/default/rehearsal",
    contentType: 'application/json',
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
  { label: "Apache",
    url: "../examples/apache.pp",
    os: "ubuntu-trusty"
  },
  { label: "Accounts", url: "../examples/accounts.pp", os: "ubuntu-trusty" },
  { label: "Hosts", url: "../examples/hosts.pp", os: "ubuntu-trusty" },
  { label: "Delete Source", url: "../examples/idem.pp", os: "ubuntu-trusty" },
  { label: "SSH Keys", url: "../examples/ssh.pp", os: "ubuntu-trusty" },
  { label: "SpikyIRC", url: "../examples/spikyirc.pp", os: "centos-6" },
  { label: "Powerdns", url: "../examples/powerdns.pp", os: "ubuntu-trusty" },
  { label: "Post Condition", url: "../examples/predicate.pp", os: "ubuntu-trusty",
    pred: "fileContains?(\"/etc/hosts\",\"127.0.0.1     localhost\")" }
];

var examplesDiv = $("#examples");

function showExample(arg) {
  var elt = $("<a href=\"#\"></a>").text(arg.label);
  $(elt).click(function() {
    outputArea.hide();
    $.ajax({
      url: arg.url,
      success: function(reply) {
        outputArea.hide();
        $("#demo-head").text(arg.label);
        $("#os").val(arg.os);
        $("#pred").val(arg.pred || "");
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
