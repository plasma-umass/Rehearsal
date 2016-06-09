# This small example has a missing dependency between the file
# /home/carol/.vimrc and the user account for carol. Rehearsal suggests adding
# the edge User['carol'] -> File['/home/carol/.vimrc']. Alternatively, you could
# add the "owner => carol" attribute to the .vimrc file, which also implies
# the edge.

package{'vim':
  ensure => present
}

file{'/home/carol/.vimrc':
  content => "syntax on"
}

user{'carol':
  ensure => present,
  managehome => true
}