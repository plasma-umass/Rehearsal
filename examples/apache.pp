file {"/etc/apache2/sites-available/000-default.conf":
  content => "dummy config",
}
package{"apache2": ensure => present }