# This example uses Puppet's host-resource to add a line to the /etc/hosts
# file and also uses a file-resource to overwrite the /etc/hosts file.
# The right solution is to either one resource type and not both. Rehearsal
# detects the error, although it can't automatically suggest the best solution.

host {"rehearsal.cs.umass.edu":
  ip => "192.168.50.4"
}

file{"/etc/hosts":
  content =>
    '127.0.0.1     localhost
     192.168.50.12 rehearsal.cs.umass.edu'
}