# This example is a deterministic version of the Hosts examples.
# We have added a post condition that we want to have checked 
# once we know that the manifest is deterministic. Unfortunately, 
# the manifest overwrites the /etc/hosts file by running the
# host resource. Therefore, the post condition does not hold

host {"rehearsal.cs.umass.edu":
  ip => "192.168.50.4"
}

file{"/etc/hosts":
  content =>
    '127.0.0.1     localhost'
}

File["/etc/hosts"] -> Host["rehearsal.cs.umass.edu"] 