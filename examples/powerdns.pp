# From https://github.com/antonlindstrom/puppet-powerdns/tree/732fa339a15d3ea1d6fc39e806f978bc576b18ab
include powerdns

$::operatingsystem = 'ubuntu'

$package = if $::operatingsystem =~ /(?i:centos|redhat|amazon)/ {
  'pdns'
} else {
  'pdns-server'
}

$package_provider = if $::operatingsystem =~ /(?i:centos|redhat|amazon)/ {
  'rpm'
} else {
  'dpkg'
}

$package_psql = if $::operatingsystem =~ /(?i:centos|redhat|amazon)/ {
  'pdns-backend-postgresql'
} else {
  'pdns-backend-pgsql'
}

$package_mysql = if $::operatingsystem =~ /(?i:centos|redhat|amazon)/ {
  'pdns-backend-mysql'
} else {
  'pdns-backend-mysql'
}

$postgresql_cfg_path = if $::operatingsystem =~ /(?i:centos|redhat|amazon)/ {
  '/etc/pdns/pdns.conf'
} else {
  '/etc/powerdns/pdns.d/pdns.local.gpgsql'
}

$mysql_cfg_path = if $::operatingsystem =~ /(?i:centos|redhat|amazon)/ {
  '/etc/pdns/pdns.conf'
} else {
  '/etc/powerdns/pdns.d/pdns.local.mysql'
}

$cfg_include_name = if $::operatingsystem =~ /(?i:centos|redhat|amazon)/ {
  'include-dir'
} else {
  'include'
}

$cfg_include_path = if $::operatingsystem =~ /(?i:centos|redhat|amazon)/ {
  '/etc/pdns/conf.d'
} else {
  '/etc/powerdns/pdns.d'
}

# Public: Set confguration directives in a .d directory
#
# name   - Name of the configuration directive, for example cache-ttl
# value  - Value of the config, for cache-ttl it could be 20
# ensure - Ensure it to be either present or absent
#
# Example:
#
#    powerdns::config { 'cache-ttl':
#      ensure => present,
#      value  => 20,
#    }
#
define powerdns::config(
  $value,
  $ensure = 'present',
) {

  file { "${name}.conf":
    ensure  => $ensure,
    path    => "${cfg_include_path}/${name}.conf",
    owner   => 'root',
    group   => 'root',
    mode    => '0700',
    content => "${name}=${value}\n",
    require => Class['powerdns::package'],
    notify  => Class['powerdns::service'],
  }

}
# Public: Install the powerdns server
#
# ensure - Ensure powerdns to be present or absent
# source - Source package of powerdns server,
#          default is package provider
#
# Example:
#
#    # Include with default
#    include powerdns
#
class powerdns(
  $ensure = 'present',
  $source = ''
) {

  anchor { 'powerdns::begin': ;
    'powerdns::end':
  }

  class { 'powerdns::package':
    ensure => $ensure,
    source => $source
  }

  class { 'powerdns::service':
    ensure => $ensure,
  }

  Anchor['powerdns::begin'] -> Class['powerdns::service'] -> Anchor['powerdns::end']
  Anchor['powerdns::begin'] -> Class['powerdns::package'] -> Anchor['powerdns::end']
}
# Public: Install the powerdns mysql backend
#
# package  - which package to install
# ensure   - ensure postgres backend to be present or absent
# source   - where to get the package from
# user     - which user powerdns should connect as
# password - which password to use with user
# host     - host to connect to
# port     - port to connect to
# dbname   - which database to use
# dnssec   - enable or disable dnssec either yes or no
#
class powerdns::mysql(
  $package  = $package_mysql,
  $ensure   = 'present',
  $source   = '',
  $user     = '',
  $password = '',
  $host     = 'localhost',
  $port     = '3306',
  $dbname   = 'pdns',
  $dnssec   = 'yes'
) inherits powerdns::params {

  $package_source = if $source == '' {
    undef
  } else {
    $source
  }

  $package_provider = if $source == '' {
    undef
  } else {
    $package_provider
  }

  package { $package:
    ensure   => $ensure,
    require  => Package[$package],
    provider => $package_provider,
    source   => $package_source
  }

  file { $mysql_cfg_path:
    ensure  => $ensure,
    owner   => root,
    group   => root,
    mode    => '0600',
    backup  => '.bak',
    content => template('powerdns/pdns.mysql.local.erb'),
    notify  => Service['pdns'],
    require => Package[$package],
  }
}
# Internal: Install the powerdns package
#
# Example:
#
#    include powerdns::package
#
class powerdns::package(
  $package = $package,
  $ensure = 'present',
  $source = ''
) inherits powerdns::params {

  $package_source = if $source == '' {
    undef
  } else {
    $source
  }

  $package_provider = if $source == '' {
    undef
  } else {
    $package_provider
  }

  package { $package:
    ensure   => $ensure,
    source   => $package_source,
    provider => $package_provider
  }

  file { $cfg_include_path :
    ensure  => directory,
    owner   => 'root',
    group   => 'root',
    mode    => '0755',
  }

}
# Internal: Set default parameters
#
# Example:
#
#    include powerdns::params
#
class powerdns::params {
}
# Public: Install the powerdns postgresql backend
#
# package  - which package to install
# ensure   - ensure postgres backend to be present or absent
# source   - where to get the package from
# user     - which user powerdns should connect as
# password - which password to use with user
# host     - host to connect to
# port     - port to connect to
# dbname   - which database to use
# dnssec   - enable or disable dnssec either yes or no
#
class powerdns::postgresql(
  $package  = $package_psql,
  $ensure   = 'present',
  $source   = '',
  $user     = '',
  $password = '',
  $host     = 'localhost',
  $port     = '5432',
  $dbname   = 'pdns',
  $dnssec   = 'yes'
) inherits powerdns::params {

  $postgres_schema = if $dnssec =~ /(yes|true)/ {
    'puppet:///modules/powerdns/postgresql_schema.dnssec.sql'
  } else {
    'puppet:///modules/powerdns/postgresql_schema.sql'
  }

  $package_source = if $source == '' {
    undef
  } else {
    $source
  }

  $package_provider = if $source == '' {
    undef
  } else {
    $package_provider
  }

  package { $package:
    ensure   => $ensure,
    require  => Package[$package],
    provider => $package_provider,
    source   => $package_source
  }

  file { $postgresql_cfg_path:
    ensure  => $ensure,
    owner   => root,
    group   => root,
    mode    => '0600',
    backup  => '.bak',
    content => template('powerdns/pdns.pgsql.local.erb'),
    notify  => Service['pdns'],
    require => Package[$package],
  }

  file { '/opt/powerdns_schema.sql':
    ensure => $ensure,
    owner  => root,
    group  => root,
    mode   => '0644',
    source => $postgres_schema
  }

}
# Internal: Ensure the service to be either started or stopped
#
# Example:
#
#    include powerdns::service
#
class powerdns::service(
  $ensure = 'present'
) {

  $ensure_service = if $ensure == 'present' {
    'running'
  } else {
    'stopped'
  }

  service { 'pdns':
    ensure     => $ensure_service,
    enable     => true,
    hasrestart => true,
    hasstatus  => true,
    require    => Class['powerdns::package']
  }

}
