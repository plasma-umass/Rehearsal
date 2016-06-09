# This example is missing a dependency between the apache2 package and its
# configuration file. Rehearsal suggests making the file require the package.
package{"apache2":
  ensure => present
}

file {"/etc/apache2/sites-available/000-default.conf":
  content => "dummy config",
}
