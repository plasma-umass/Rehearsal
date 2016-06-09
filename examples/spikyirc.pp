# This is from https://github.com/nfisher/SpikyIRC.
#
# Requires:
# - Designed for CentOS, not Ubuntu
#
# Changes:
# - Facter variables defined (fqdn)
# - All files concatenated together to give one, 400 LOC configuration

$::fqdn = "foo.com"

# Drugs are baaadd m'kay. Don't do stages at home kids.
stage { 'epel': before => Stage['main'] }

include apache
include collectd
include collectd::web
class { 'epel': stage => 'epel' }
include ircd
include irssi
include ssh
include users
include sudoers

# TODO: This is a pants way to manage what's run against Vagrant.
if "localhost.localdomain" != $::fqdn {
include postfix
include sudoers
}

class apache {
  package { 'httpd':
    ensure => latest,
  }

  service { 'httpd':
    ensure  => running,
    enable  => true,
    require => Package['httpd'],
  }

  file { 'httpd_conf':
    ensure  => file,
    path    => '/etc/httpd/conf/httpd.conf',
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    source  => 'puppet:///modules/apache/httpd.conf',
    notify  => Service['httpd'],
    require => Package['httpd'],
  }
}
class autodeploy {
  file { 'root_bin':
    ensure => directory,
    path   => '/root/bin',
    mode   => '0700',
    owner  => 'root',
    group  => 'root',
  }

  file { 'autodeploy.sh':
    ensure  => file,
    path    => '/root/bin/autodeploy.sh',
    mode    => '0700',
    owner   => 'root',
    group   => 'root',
    source  => 'puppet:///modules/autodeploy/autodeploy.sh',
    require => File['root_bin'],
  }
}
class collectd {

  package { 'collectd':
    ensure  => latest,
    require => Class['epel'],
  }

  package { 'collectd-rrdtool':
    ensure  => latest,
    require => Package['collectd'],
    notify  => Service['collectd'],
  }

  file { 'collectd_swap':
    ensure  => file,
    path    => '/etc/collectd.d/swap.conf',
    content => "LoadPlugin swap\n",
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    require => Package['collectd'],
    notify  => Service['collectd'],
  }

  service { 'collectd':
    ensure  => running,
    enable  => true,
    require => Package['collectd'],
  }
}

class collectd::web {
  # Too lazy to setup another configuration for collectd.
  package { 'collectd-web':
    ensure  => latest,
    require => Class['epel'],
  }
}

class epel {
  package { 'epel-release':
    ensure   => present,
    provider => 'rpm',
    source   => 'http://dl.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-7.noarch.rpm',
  }
}

class iptables {
  service {'iptables':
    restart    => '/etc/init.d/iptables reload',
    hasstatus  => true,
  }
}
class ircd {
  package {'ircd-hybrid':
    ensure => latest,
  }

  file {'etc_ircd_conf':
    ensure  => present,
    path    => '/etc/ircd/ircd.conf',
    owner   => 'ircd',
    group   => 'ircd',
    mode    => '0640',
    source  => "puppet:///modules/ircd/ircd.conf",
    require => Package['ircd-hybrid'],
    notify  => Service['ircd'],
  }

  service {'ircd':
    ensure     => 'running',
    enable     => true,
    # Use reload so hopefully not everyone gets dropped.
    restart    => '/sbin/service ircd reload',
    hasrestart => true,
    hasstatus  => true,
    require    => Package['ircd-hybrid'],
  }
}

class irssi {
  package { 'irssi':
    ensure => latest,
  }
}
class postfix {
  service { 'postfix':
    ensure => 'stopped',
    enable => false,
  }
}
class ssh {
  package { 'openssh-server':
    ensure => latest,
  }

  service { 'sshd':
    ensure     => running,
    # Use reload instead of restart so the session is maintained
    restart    => '/sbin/service sshd reload',
    enable     => true,
    hasrestart => true,
    hasstatus  => true,
  }

  file { 'sshd_config':
    ensure => present,
    path   => '/etc/ssh/sshd_config',
    mode   => '0600',
    owner  => 'root',
    group  => 'root',
    source => 'puppet:///modules/ssh/sshd_config',
    notify => Service['sshd'],
  }
}

class sudoers {
  file { 'sudoers':
    ensure => present,
    path   => '/etc/sudoers',
    mode   => '0440',
    owner  => 'root',
    group  => 'root',
    source => "puppet:///modules/sudoers/sudoers",
  }
}

class users {
  user { 'deployer':
    ensure     => present,
    managehome => true,
    groups     => 'wheel',
  }

  ssh_authorized_key { 'deployer_key':
    ensure  => present,
    key     => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDHB/a1L7iEH/SMUBukLpUpCQgZboOEvc+0RHMQZ0JMC4iaxzwoAbbDRUvv2T39NRXaojk3cgAQ9D9piN91jU9qwgVTTRs4smHs/A1yxvlsZVL879Q6pTBQpXFYMCEL9rSVQtHK27mEVht5SOoephKoTgA2icOqtbNFdWyb27v/CEE/k9sKI4igJsIbLzhjN9TYQf8LW8d9DvCuNbgXSYUK6iK/7w6hmAlHMXhCSs2LsvkjEqLSgCgUo0FRnUX76dGBpoDNKe6jryPKMlGZN5A73yOF1mpTSw33KJRXi99Uq1jQiQRfIgwHd5YSaX/Q+4xpdBaoAyh5+A45fQBGmT63',
    type    => 'rsa',
    user    => 'deployer',
    require => User['deployer'],
  }

  ssh_authorized_key { 'deployer_key2':
    ensure  => present,
    key     => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQC5U2M4XmaFit2AMOtrP01im9mkmizrl7heUq8KXXN+BFYLj8GMKTQSWpfb8uB7enh8KKuqhZLQ4FXAxY+j11UTDWmSAS/TMrj30YT6ZpKvKKO8S+ossqxoYaACiS2oTVVOtwkcoaP+S3uRjmH4crIOhuYiGbzGt0XLyDv9aH2J8bVWqcWw31P5NjzTAKWNhNfxFOVdRUissUPTxndgzow2KXJ51c50zWMM97rufseznqvTOFMrcHag7QEcxe1LCKw/5RkUD8exAn336Hpcq57ipJvVb5jU6Yz21QIGuQgsJ6c07BASGGnDqQljO4NCVdR/ftszvQ56s8gUPqe/bkUx',
    type    => 'rsa',
    user    => 'deployer',
    require => User['deployer'],
  }

  ssh_authorized_key { 'nfisher_key2':
    ensure  => present,
    key     => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQC5U2M4XmaFit2AMOtrP01im9mkmizrl7heUq8KXXN+BFYLj8GMKTQSWpfb8uB7enh8KKuqhZLQ4FXAxY+j11UTDWmSAS/TMrj30YT6ZpKvKKO8S+ossqxoYaACiS2oTVVOtwkcoaP+S3uRjmH4crIOhuYiGbzGt0XLyDv9aH2J8bVWqcWw31P5NjzTAKWNhNfxFOVdRUissUPTxndgzow2KXJ51c50zWMM97rufseznqvTOFMrcHag7QEcxe1LCKw/5RkUD8exAn336Hpcq57ipJvVb5jU6Yz21QIGuQgsJ6c07BASGGnDqQljO4NCVdR/ftszvQ56s8gUPqe/bkUx',
    type    => 'rsa',
    user    => 'nfisher',
    require => Users::Irc['nfisher'],
  }

  # The user used to provision the users from the bouncer app
  user { 'bouncerprovisioner':
    ensure     => present,
    managehome => true,
    groups     => 'wheel',
  }

  ssh_authorized_key { "bouncer_provisioner_key":
    ensure => present,
    key    => "AAAAB3NzaC1yc2EAAAADAQABAAABAQDLT2Z1kNmeNbfXWDGtwei4TOl/tgW8RuyEp8FVsjkoVNfqNSUOEFhyYekjh/y5TYC95i6kZrBvKIsXO9TCmQ0kxRrhLwvwMMXLAF8QTs60bote6ExRL1pSNwmYP92wUpnJ7o5zMSUH9Pm3HKeAMSQ6sLZYNZ9VKtU07/zFbQfYKVBVd1pRjr/atpJ0Z9qkiYQbzqLyQUoKCQvdastsk2VHzgXdYnErhYH0E+Bg/1MqEVUZ/VpYirRe0FiKXzdtRq1O/cYzgOHtq1rNCcr/jzOGqHD4FsCJ29Jamksk7jfNC0wvUT0uPdkO0gDm3gMU3gCVTO3BJn0kTSFNkBNm9qC7",
    type   => "rsa",
    user   => "bouncerprovisioner",
    require => User["bouncerprovisioner"]
  }

  users::irc {
    'nfisher': fullname => 'Nathan Fisher', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDHB/a1L7iEH/SMUBukLpUpCQgZboOEvc+0RHMQZ0JMC4iaxzwoAbbDRUvv2T39NRXaojk3cgAQ9D9piN91jU9qwgVTTRs4smHs/A1yxvlsZVL879Q6pTBQpXFYMCEL9rSVQtHK27mEVht5SOoephKoTgA2icOqtbNFdWyb27v/CEE/k9sKI4igJsIbLzhjN9TYQf8LW8d9DvCuNbgXSYUK6iK/7w6hmAlHMXhCSs2LsvkjEqLSgCgUo0FRnUX76dGBpoDNKe6jryPKMlGZN5A73yOF1mpTSw33KJRXi99Uq1jQiQRfIgwHd5YSaX/Q+4xpdBaoAyh5+A45fQBGmT63';

    'dlewis': fullname => 'Deepak Lewis', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAo63DoaAWYBwyVemxdmnyjLAod9FjgL6lzRm5rCjlGBVZRYL3qxXzqZTK9dCxh8QKzjRJdKlG8HJs7cazHwV9aGVF3DyNCQVvJzD1seMlmNjEWkA0o2fHUIB9VSo5p0eD2WssUwN+Fw9jnC299C0aAS0AfqYUVxXIsiVgemoQVqy2p5yiKLomwDN80SWwMgAFvl1PNmDbv9NCB1mCfbsyUZ3bN4zStuUXZ/JT+dZxywbX5O212TWIouBJeiXIPn5kPO0eCW4nFvlrlP2RerFNLDiOuqRVpbWSZ5D5MS1wjXmstzgoOOBvUCUgwnXrNG7CpELFOLCJtxl2Dmlwomh/1Q==';

    'nferrier': fullname => 'Nic Ferrier', key => 'AAAAB3NzaC1kc3MAAACBANuM1SkfYrzGXYyg8bIqGvGMr6otpJQ3UEq6LdZDr0lQDLjV6YaAA+s2E/Vks9fCTwBzJ1y6wzEh5dVR2XCaxtMHcJTdBFBZNVnEUjx1mkuxaQb3LBWBlXrA/8ZfSC/eLaqwv7hVSbsZTm7AsO+fcp1O07YesnTOHer1EpmM4vuNAAAAFQD3s+lUUQzd8OMPyxM1b+xplGpMfwAAAIA5y/RpW5+xb3nsQha6YiJ8HSws7Vl92KV/oR0RUWwty4UgRhFr/6gQIZKX38Vp4JRbzflvFfCpA+7Bsupgsdd1GWI5NY199c8MnbXhKmnaKQHX2PSaEUdp5mePqYF7vj3lq1u3Ouuq8x+k9gn1PzzKK43nzn8JOtRMN0vpImmoCwAAAIEAlmKKcQNchCwDKvN/mUHaXplvm0vmv/Fkk4ZD0aIvii+wzRSSOFyuJ/oUVN98kXf2W9kgQDir6wnAhuU8PSZwKKyDwv3r0JVWm1XZkbUvoGYCXYEzufWy/D4thO2H0SUuoZ9RGdtSiEFyTSZH4bzUq2tGVibZuCPquDPl5AzRVIE=', type => 'ssh-dss';

    'ihassin': fullname => 'Itamar Hassin', key  => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQCgcAeBoU9JsOoysDmP7h2PvED6r9jc1bO4QkzWmI3BhNEs4hNsmuGTOonk5gzgVQGAxQh9GDJyhO0HL2PVqIWsfvS8seqbs6b2pio2O874Jfs+4DQaqzCtA9ca+bnb9NgA80tBDMhrNT3iNVtAsWNyhqdglr6tXZb5S/10FZTYlJb7tWBIAFRnpAOW2Gk3q6B55x84RoklZJXxfdoG0ZfhMAux9DsrtevZfQPcbBys0yLcuIODZJaMUfaqn6qBisZfj3FPSfKFyFiF6wR2VroWDhFBIs5NIx9b8lPH8Up5ldqK7hhFM+mOKLetbfyLOo3+3NHoMxIIb7Frtn0uZBsz';

    'mryall': fullname => 'Mark Ryall', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQC7nrBRrLteSHFD1WVKjBDBv2OvpCk+/go/TERzHViiPS7UUQlGwlw1NRo1qRB0EDJzzvZP+mo1OuEdxmMRLbCibeEYceLpHyElS+Eap+QJAHhBVcB0tJe0dI/09jCPo9NepQ/sOlvXSmgchwvXx2qkEp6KmJ+Si8Z2sbCC47V6LV+UljCy4xP5rfYip1kmw+n0rQK66w6vBtDP6Uth3meYockdOhu2ZrG6xn03eXFkKIv598+2ZgmCAuH8jsCOzVzYWZxnpRr2GvtuWnU6wtCYsMPTQ3JljBhJcFyvol5P8jn+ZtbvToz/Kw1QhgMAEyFYw15QUdm1Zxq868gVK3o1';

    'jsmith': fullname => 'Jennifer Smith', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQC7ycJbZjZrHlmB7Ii1ir2R/cU4bjLKYB2aOWAhjCgiUz/RviRnlTFwRtOk6RQ5Io5r4X1s2TvtwxC1jSwxONrQ1mD4yZjBjT/HmwDLsRDH2P7oyx4yhhcu8Ih4eGMIq81wL65DDZNwM5/jDY/5ge0d3BeUebA8hz6Dhy6pLg1cjOH+fbMu9BH73H7FDJaBTFJBvOalkPTJwv4SnnN9HfauonNkK2rb1ODG5P02azVTXEwOd3dgg2NkZU3AyKHUwfAAQgpUMsTIdP5Kbxy2C1amRwQu/rlYpP1SkCgt3ZvET7+iA5Rl5EyNJghqKa1Ymb8gjYr5wm8B2QAY1+cr87xP';

    'jgray': fullname => 'James Gray', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDEk/vSOfP/WS96ncFIvgrmzqr9SJeDR9k9LwrqcS/xcuyQl3l9GmqTAI3Cyz1z58qZRKfigE+cUjX33Kun6Ms8D4f1ubi+sT9kqI64QCHCpKRgwaDe4ocC30khxGvmF0Kn0gaqf/AoUTmXSeDpqV9/Id2FXGfnyoMnoKI/kS92heAJJWAJkvlYb3rcFlVBZey4rVzWDcsD8QjXsP4N/cbDoPxAzXhLcU9VWBHhQyBj3yCdqMs7OXnu7di4ZDzJugnOaV5dRHFLtP0uUxSBvoRoSO5dG9B6WM+xr2igJZvIYEiINTtwV+Va6aCe0fBww1ExnbsK00n/O4BOATjMIPrb';

    'sdqali': fullname => 'Sadique Ali', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDZt5eGKHPSWrLNqTd+DqoZAF2HHDL++jzhuP3xgHmGXtsC+zT60dqF1ojBVGBnB7NxKWowwluC1KkRWpt7zBsEiZACpuNSzbIH5UXGOR0bPR0sSULf8WdokhIwUDrgdGh/daZo5FiXxbgYaadlhpBw7SdZVbYywyzMtxlE0shXxnzMoLyodHNAK/vN8BN7ITIcNF0hfJJx/C4N2+muBiGc4pBdSiiSjsZZyIycmT+KwqqpCdS5ZVIUTdfPyVVgr+Qpt8SbPbic7JXgQ0Oh6Vx6bY3xNCvh5+em0lvTZ3P5o1W37H/NjbHTH2qL7/MSecFiVsvxivzQpUE2nbd/GLgB';

    'jbrechtel': fullname => 'James Brechtel', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQCh10B3Vu0DU3+7Rf7yWkvRUEtqSQGZ+CedT4vbrL9/1pe7k2rIdEh4RRHEGIyHEz9f1csHYrVVhJnce3vXB5yyvB6FKD1wHnLvwp9Rc6PKilirofEpORTwjnPVgC1aUrI2rNJHhqUbuJ7wTfyjOGxGPYQ78nVopLmPq0fYSZD4fgKyn0uzic5Io1H65yqYyKA/O6/EfxVmEP7HmBt7/G9GzY+KEI8I2ze+4N9UXwd1E+PjWg2tXPKT6nmUBfeLsrCprETOnCWD2il8r3+YHvNH+EQZmVpdp8HIhO8T1AVRy7Bw7e7wU68lrWHkSASVvWO7ScA3pyRf8OI4LHsdP3Cr';

    'diptanuc': fullname => 'Diptanu Gon Chaudhury', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQCyip0nDLs8IOhByLl3CgEO6wGB+zGaqULVW2K36DNrHtFb/G82fKTegtTEDjeezx33JP03rw+2mFgqQTZOmZyOep4WG52Aj4rlYBzDPAYdiA0xWYi35PY80QI2ETn/SxVe4mWMKEN3J6IUut979XO2h5Lo6xOeh69pTMwwQezwKeEqY1YI6cEd25OcGdDDY6OZNH83+IZ83oKkTcPgE1wzFiMssbuEzt4uJjrA3r/eNRCcZPT0GVQW7p8stYBkL039LTrsJk7jgkuXhtiRRif3yNJHkGJ9KOavpvmNgSPZqtyt6QlwmJD1snRbTJvhTIfPe7tDYTpqKngT9ZIPfjYl';

    'matturbanski': fullname => 'Matt Urbanski', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDIdknX8zRzJLX4hXr/0+SIo+D7LcOSkgKYA8B9sW7dMy4sCey9KOGga1NA/qgEBknUrKEn9ZEktwYi/YaxqfpxrkuWjNBWZQa8/QCmMH7PKFqTTg7USAyHw0QbWqMcuFzeib5TViDnomXq8rR5EV/PFaHnA7qXeQZUVzW2ChoG8m2bRMHRsxrCaERWE+4OrbBydcFyJSk817Gcg/NOEQvIk8BCgBGQ9Sj4hJ8ebdcaATZ1Qw7NgkqsccnzwyFQqjlLSkacjiHrsZhLz5ttrluPZk40ChWusdWkIwJzaG1xr3UqmcANUik9App/zZAkBGFfykZf77Y97su7TQjK9mK/';

    'srobinson': fullname => 'Scott Robinson', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQCsfrqyDxMLs+LU+55NQlGEPsucDRe0phRS31Dc9cbIB80ptxuoKFKO8NtmHCsb3V9juVZrHFe+I9p+4B2YiERqpUe8diyzL05L+dhfvVJm2t5QM7Qp6cyQv+fnUmCc0B8Q2G8Vs5+it72s/DyymiGNnzkpKk03zzCxH8yMe7cqacm6dqmZZrdp5JUnYLOXOEK8Bse4kxBWne6XE3JuWP4IxN97XvehLfxvHvldu9GUjWKbZeMns2jn0J9ST78AzwDhUiUFXV2nr02GBof9A0dMA0PXnVKKzy+rYJ9GL1mb8a6rZ+rFn6K//sPGINcoPNQ3qtqLKRUfLHy5ZSyCeVpj';

    'benbc': fullname => 'Ben Butler-Cole', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEA1Ta5Gpqtrs5qDYfH1aN2qRJoIwGc4gzJkeomI7dCug7nM8mpGvGCOmtZ3ZPPZbMMRARx4O7TH7RaeVTqUA1lkSI8vH2flZSCmjl50liGfRVlzqrfhFtH16Vj+j++SH7iHyBEx1VF7tUc+NM9ETJnFPaOpko3DFRvxsAsF3nyVVQuJLFAnKgElA2wkeulD3LErE+SpaS/tDOfgXttnwHdC4JdGrDzijIw7ojYsM623YQ39XEmYUFVCLF+sv7DEWH6Z/8LxFkJ23JDS/Fo4q7BMhRk84bHMUC99yAVjp1HjpvK8j4xQsWCcrdYCbisiGGuVdIWsETLYGFLO8Nr+t39Bw==';

    'pgm': fullname => 'Peter Gillard-Moss', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAqybTrrVOq9OT1KVLk1Fz+gyZF/CXeolyAMx+JL+OGOI8tRMhftQR0LjP4d3UBy/VJqqYsRX1gFTL8DqyfC4H9utpgzA/ji66jpbcp6PC/PtNVJn2FPQT8gpX44nx2kyHDmTk+ChyQ0hIsQAEVsa4vqWm6R3jmwXXeLS5iO19rfE2pLchxlRI9MFwyayutJR3CPFeVnkGh1GcoZ9/sdq5fRWSJops2lDzmC+JMIdGE0HRqsvsII/kLN0WzGyOqDqO+ek+Wcj811kCKzwSXtW8qNQDVErdC4zReYyuiT381nViz6SvmDvuYwagGbvTQqJ9v/GzkBeWtKHilM8P9/zLKw==';

    'korny': fullname => 'Korny Sietsma', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAzKmNl42+3Sa39q4kYodfv4Neiu78dWSFdRGF1EcD8HXr441c0YfcH+w1E2O6LI/7xd4n9cd4D9YdPZVdbG60p+289ngWs8aaHplhAfP3J5BZj1VYi3pASdMPPZa085zIxkp45Sj5kaUg6mKlW5fkPabHWvrCHm5qcsy92el1E605H0xaN7UHVh7ul/wEoCCW6UehjK7d8DsbRN2XneiHYnP7IYSIEINZU69Y+Wd5w6kM45X+ghv1EDZy7suQerJ04g4czbV9/e45FN19WnjDgjazi0QOaCwhP+RlYTZqondLnCRRemBSULuGM7nJKG4+5TWaEd1sHIy2hSC6+e7eTQ==';

    'tcowling': fullname => 'Tom Cowling', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQD90tyonLAnLlkMhlyKnDGmAx6tBWVLwsw2K2cE7XDT/EvuRaPqlHDcwCPJ1hlhHF87e+y6QT6a+tH/RU6yxVKtNP+MGof6wiYjYLYZpgNo8usYc3fJwmuEel/xBfZAs6a/AHko9kTHG2r8ea64dYWSqWlFiHPgTrAtCt56GAxz2HerSjIFmb8UoODkx1F9bz3Axo7MIdEKeTqKOPxrYBkH7xQZ7VsJsQgYPbHHRV8IuxNC9+4vXMptTaK5OoZWX6ynherN3Ymr6j7Kt+fMJKWrVEJpd2A7qqgkUNJOKwESh/XIKFU3Eq6FshfThMhpc19Nu33f4WsaqSrOm9aq5rmN';

    'jim': fullname => 'Jim Gumbley', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDO438qmPAuFEnhVsHeGY1Mx+INKDWfx7kOAEYg33qw5JTIIDbmHjPL3jm+dS3BHra0yhEz8Rq41Q+SdAJVBP8TNeofABCFyuoWJ/S/3HZHOSff3BWbwsdaHssHCTE1+w++18CUQo91lmLnkMMzzILPPALFnEQnp4hAeVZqkEa3AIsQpRgI4wFUhFXiR5+CWdwfGMQpvPVSHNQPkd5jCT4ADkLP25RhG/rVIrQyQvYNE9KQtl5dHvSK+VTs3YUyvgEE2esJ64NIoD7vFauCCGn5C39jEq33lny26jNz3/XKEQCPgeaZ4F7pXydxdzgVu3TvXb4NECwibQAGFhEs35Ur';

    'kief': fullname => 'Kief Morris', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAwaOjAEESTdtZzrircWLPKTZ3Crt0nhcak78ajV8b2etCaX1B305EZsqO9pi0r1QfeDufqaxLoEyytxoPf3OCkGgspK1G2JBBaNpvQzK2Jwn+OhddB/PiAzk+4ZITnSpANyoJymmHe6F9hbQzKBnjGl3Z8IqIDaqQcrkWSoma86bcuTQ55yCIuUtdDHD5B1E5TDX1FjYVwF/aSosaCzLs2mGNUFlf3UeSNDC44VS9uQaUqWRSoAzbc4WMLChrxByn3bP9g/QtX5Fw8OrXNOLJeG4f1DTmIfj20/UUcXjyaPjtNbpIaJx2jEh1awoV0Rh4v/JRcgZOPGkv8U16fyZIEw==';

    'gga': fullname => 'Giles Alexander', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQCykokcyuJ5dW3Ne4Xs0FJxNRZ4RoYePKswgIYgy8rca6ukbmODH4uP9/PKElBArZkhlVYxwKy39JfcycLnTfbRByNyuP61Tx3Df2NS7FgD0VG9eKoVCQFEoHY/hxYMriFpRaZlJJZBBo2Lzkyn5LSr68oiB8EAuw9QCwejWPk71aMfIceEjNg9xRt2x6pqOi+xRUzWTw82a4o0gK7Cuq04W+VONqHWT7LDQSWSqYP86QSdkJ6EZOJwCgclKYRaolzfxYZRv1bfDd80vKvYOuZpLl1zlbOCcqp7SEIqZovIObZirA7NwcLMURu/oInrxTNsS9jPOnH9E5uTa1OitGZP';

    'chilton': fullname => 'Chris Hilton', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQC0ridBAPshP6tvKALCgyZO6CfkQDQVSPZJuuofGzMvTCTKg+jMgwPiGDfyyNrCCW4elV4ipnXK/uKgzXEnZjA6FP1duc6iJBOsK1gJ27pXQd5eyKqJAk7vmzev75EmuOLhUcHNQyiBk2gzWNljT3HMR7NRBnmZ2h95ag8pEgNjGTVUsVBFU8Ga9sZLyV/9pJL5Tn8q5MDguiOP+e7xjzdEuTdqU9U5FjxLZggTuYVZbzoqTg/NJFZ+MQJHQqxU6bl4WAOpjGmoj/tzJxjDWiTlskAyDg73UtmL1G9svMTnexStrrUoeTfvYEKeGNtrJfokxFGWybVHJGalcmuQDT2z';

    'pdowney': fullname => 'Pat Downey', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEA21UcaXzHeWbOBa9vnxYtkIu6ugdV2E7/MFDpGGnooAql+s4LED41CTbiF2ECqwzpdPN5ty+wo/rhTStj8v73Ob188V6PMVXMGllT4lKtsAjPM+Rxy3HDh63syAIxXP3NbjZY+dKB6BorEuCEdXnpep8PycmcCMxqUf+cTwSMwW84Xqb/V6xIft2HyceRrIY2g7gPKpOewRbZ6DCQUPBt21Sfn2rc/18O6c8viQ5EI2yhebzPc0DMrl8NiiOyDnIsEefFSSqZ8m610zOXxqqFfTrqYhdXq6HkcJ2/bEiKfJLdz7UwF4cFUgKimZikzfbVWPmg74Cf5ntEu4ppnXMtxQ==';

    'vhasus': fullname => 'Suhas Vishwanath', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDDogJk0g3K/mBiCmHmQtL3JLivcrOu5CDUDXApmf33hR6ImcAbFT2oB/6Dw8u1WYRrccTYZNz494GfQtTnL6eNpCMmeo3IfrkvwcMKhtWJGsZRjl03sOeLKg8cL6ECzG3aL4sK/GoPqi7w8wi7f96nI2clAUKWQ0TbpU+JAVtMyoyuGQI34QFD7kHH1bhzgNIvxWqWESSB4RLPVxp4m9hBIfFu3GSB08AkiU8ah5clxB9m+wFJVzIdAGNaUQVvUgn0Yck3thfdYQtHRjFtmPEttLBkSrH/kIQBBt9eUgt24BVw0AMUEEqE41I1YaEslIt5x1BOC6vY1AqzRTXG+2iz';

    'ayates': fullname => 'Andy Yates', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAzD4qFloI1MyJ4qaks0SNqT6Y8W5n2K5o7DAFDqc8gt+g4ECPfjnaksEirFAku4vp4O7Xv+cjPzDET1eHPPDPegaaEs3ZHJtUf6SzrF59IOzI2PX8HZiYs4UnfB1sh9dlHpSLQ1sOblxPn7VfMd+CXZ7iAnSZ4iwCGGAlNTVGXHBk9BtYemRtQLNLRaucLj+e2lH8j50XIg0i2tMubpxdZrxynTpySkrVPejd0fmQKFMpq5Yn122nmKp+1iaAtnM1X8JUO9FRy28F+kmoY9ilNfQXh0AVqnnnjhtRUq6zKnRMWgq/He/mSzdf16J+Psgf3aVoBFq/tecxBQJgRNpDcQ==';

    'philandstuff': fullname => 'Philip Potter', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAxPK5a2PwUij5Z+/dYKU5oIBVugDOY2QwuXiXsvo8xMoE9AMWYnLmDTgGVZ7VBqtXXuOhvRl7jm38xsFSaB7R0Z6ej48tmemnRKPIN4m7ahUua3Gdlr24/5dXB9QUg50JZsekF/drQivjfRhSTNFOnBhdcfkYy7Zi+tShRHJ/TWpMjFkZ+EBiW6GPYZqyBTOXV6HdPWOePxJkBRpEHcWxoQ+3uBLO1uCOxNAX10d+maFZ0Ql8zEvdAZJqMprC7LCBKKZBp5pw8mgSAIAHsPljnZ+dvabY4+WBd0qOvk4iSkjWad96LcPw8EyvhcL0+PK4gGLD1jb30y7kwxCc7EzggQ==';

    'rlengwinat': fullname => 'Rene Lengwinat', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQC7tRPXS85hnDtrgYaXl0jzIByAzAJVLeCiw5Xqj3LSpPb26cUc+xQQzIfWgsMxF7xWZHLU6Hv4tA2qdXh72SnNK7SvfkzmUjJdoT5CpPQGAN1zHzdYopdgcBlwW53XazlCmiGg6HK5abBco8Klt8FZpjtaAMTgAyANV8zYdhAPf77UrcyPb+vtqFMzF5d5sAv9HE2WgoNx8ZavUyEDkUAtNKS49ponuiGnODVGUv9c/GzQPxPmaPQxsrLwFEzeLl+/jsT2ckvS0WSLJ9+1LKfkNtk7rVs0Vw8sxxstcjfohDUdQ94IfoCdFvyxPFmXSCs74CtK9Ah/yrilFVvKzN0/';

    'srnaraya': fullname => 'Sriram Narayanan', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAxaGY2d//0GKD1J7a7sycbbhR6UccMKu5FituCYF47HVevJ1eOSdyKJhaujEApZLvCHnrFZX8Oc5RtPDxs78fllm+baXcS8Mv9v85synsEGfywwn/yO+1B9HXEI8w+t0F4Ag4bCZH8PpjOLG7PHtcKHL0Yu8sIYCNF6i2fnxwm1HUPqRLleY8fQQ9ubyay0uNcmfajWacL8XqPST4rUjsZqXDYDg53sqLncFl9ZfEdAOoZuRKzdxTZ2DkeSRaiS95wnlaAmV0DckWGD74VlRCld1Y1RHzRIA291Kgp2Fts4vfOqACAnZGzqwupYNFyzo0YOqBmCpd+a9Tq30lnn6KZQ==';

    'skatta': fullname => 'Srivatsa Katta', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQD58bHW1eZnx9y1LHc1EVJAGwv3lOlZS6ijgA1ZKlUF46p7V04OHtGb80x9Jrmw+B6evw2GGDo9u85hsmx45R9ofu2S5Ne/r5EZS7lEd5b4/clcRm/tzxW3uobjb6UrKc5Ud00WRpdPqATZ/lg3K9f2KiTmvWj3AfdvAZbDqw9ghSOuWRK9drecKvdaOSwyEY7Z6uFHxwtDU4bOX7EC1SSuMyIpKityfA0Al5sDZYHraHallXco3L/IUsiIK+LCxVXwz9RN6OUN4Kxlpvn3sgpt6KAF7DyVlQvfgN5lOT/Nb8Acc6lMjZyFXU3YPWmzi7cEPNS0BuWpapnBOeU9d0UV';

    'olabini': fullname => 'Ola Bini', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAvoPem4sP/mvtXkZuILrxCkEmxHm7e0isdd/vKkOcodR3OoVIFxZGT42LpGC17GAOmJfobUTBNAIZKKdag+qlwXSclkqRHaKKc8EWU5b0MKsg6CPcbm0rcHpPxQ07shR8MDXqD2AI9fgxvMoKfEVQW/HmZDc1BJA/IyuQP/Qrx2zAaTkU1ZsWQK1hgb/3fJkt3e7GmYjeZr1p2UHvxkurOOCAFtz54XzvT/Vvjt1+AYpp6Bmxe6oD49B8KnjTURQepmzbapl4HrvcNBTlcyPAIDhC4COrfZm40wnpObfo6WZdN/6z662zp9vi0ZwfcOqq2B3opKt+o8KxE/MAmGruCQ==';

    'gbrooks': fullname => 'Graham Brooks', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAsREYz4cCfQyr4GB/veOGFzGCVtkBxzqoAO72ycEqjpc5Ko577/QaUwNGmomfuzgd+DfM9XA4WwXf19Hr0MIMjfg+rqjCblszo4E7unt4Jrp5QDAbAoNM7U2Mvj9lNgmEtZT88d4OtyQuWnXAu3r895AhqIdc7OYghALAQXgaoZKMbwarR/WvmsieFw1uZ/0im/AeuBczi2EcXFVI35REd3+Fijm0KWOx5Xwckn7tYccdKw5vLZJONEZF0PVwgKgcGz6KOfAZ0cBDTNFQltk9MZ2ocM/I79uzCt46mXtkjPnvnLlaoR8KQMIpoSV+N7kyo+Mx7OXHwf1XTG1vWRjVlw==';

    'mneedham': fullname => 'Mark Needham', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAmyVxx/ZgiTUUL6/+3+g79wLqaq1XMoAldO7jxGt1p69w+3IktMEm5hD/klm/HR3FeUJmXboEFxpQwduv63qSHZ/VmtYmbS5+XjL5xRHwAcKpQwWj4uv0/d84XkA+33hKxLszUxIu3dZJBc7z9ZIVvVXXmJmSj5dEz3aiA8GHzHiOz9kI6Vm07clP5fHCJKh4o/u8imoTvbldc4Qm2lrugnwk3hkCwVIY5BDRwek7voZAEM98CPiqDqlJJUo3uR0HaCIPdo+M1gUKa0b3zIb/Z9yLWG686J2K+GfWDIADTJRzcY1esoH5VBsusGIH365NDzl7wS7Qbz82LqBHwTajLQ==';

    'ocelots': fullname => 'Ocelots Robot', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDgcwmoskr0wiMeS7dunSBjw19SfDr4dqTKRo+AyrRY+alCVCm6o/WymP+MAHdvLU7HLTcqXFzJE7FJJIeltWMaMYmM+RX8P5UeL2p2a5H4elLU66rZ+z5DagkCOvy81ZQX/gZ+zOoIOk2rt6EQ4lDAq6u6HKeHSnjBWTz0zTmCQyrcOTFPp8r/VdQ3K5oivtg2iPq9QaTxcnU2PisU9BmJNm3ednLBJ0hoXyh4TnF7KvI03vTT0vv+TOoYu5vMOiuKJL1BWw1k1oOvbSzRMXu/ZPVdc0WraKU8gUsSJg9SvAnIBEzabDwT/HLwjkOuUius6xP2ewkIHncm4OqiqB3h';

    'garimas': fullname => 'Garima Singh', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQCVHp5WGK5UseUbg8Qz7x7geR4RInaM4Zrmd2FeyW23I259otKrqaeRJLzcSaliz4esRCWYopK0q5iTjK4oOSgr4BM36L9B7+YYIDcsG9F7FHoabxoDwrfzWHYhknweQpZ0S78oY0PUcGiwSXT1FUHHOTyMOuYETZ6/BKI58+dwUz/tj2uCG27fXXZozQRxOtUQgYq7afGRpifiDFX6b8z2gjhM6N1wP9WyqL67cuXO6jGaMDDKh8eLbPswOFxcBn/rqjUpVfDVqfBOAbL+6OgpJKdTZQtCYEgW3d78scZ5DZazFGGQqLQ+we8srov2ev4OISPjSHnNxN/rrlSD7j1R';

    'khao': fullname => 'Alex Ong', key => 'AAAAB3NzaC1kc3MAAACBALX/7DR3fGEWjjtKG6l3OUytJcyRfXFcnnIQ9K9Vg63eTy0vsAkWgxRqi43AtoyfiZjYtfusgXKeORoMNdYgDRfr5pYPp5yKe9jaMt8Pfzi9Jni6rRuTOrIzpZlODi2FpQkon80WcFTdREUHoSRXMMOO1GEjsFXyWHYQxKzSctCTAAAAFQD4YSJ86Vi2+i9bGlDG7xFOJjgDWQAAAIBDERJjfyjNSCX69oI1AM26W7PBvzgWFtuPk6RWPttiZttiFr2NyUzzDvpNe1fse4hbt6oGK7HsQt1qoXN2lLAj/GfeUVL1B7AJwIjt6zHc1vBsB2LkxeXn/QqMJe6XdI04IICdqzE03Bpru1SX9R4M2pV79h7bzWu+2jmfWAyO1gAAAIBK97YGn4cchGM/CZW+NNqioYxKd/bD+F2d80g+mCLdwpKpcGp5OnmqbflgzgyOtOa8LFUvZ853RcaA+Tn/2OZCvnN3ixz1bgB9CCfYaAwbmQthcwT4V6KOv3NfN9AQnPu7ajs1APhAkzz5sbEPu/GHPwi83IlIdn16ZQFzv6bN3w==';

    'rmiles': fullname => 'Rob Miles', key =>
'AAAAB3NzaC1yc2EAAAADAQABAAABAQCdwvq4Dy9UzooYMKU83o0w8pkqLYcOR8zDWdCzJMC2kC2WhIZBOi9yr2YB1jBolZzSVUg9w/U80lUxCgVOF0NQWET5225C1LJmqyzfq8I3u+HjvGioDCuwnTflEdbEkBUETH3hTY5RsMJLXQf62t9V20hKLHYjTS6LqqsPMAL7LJXZ1/opQSLc9z2BFdvYGX4Zlv4f3SJb3KSiFNG0waBGo7c0+8nI0KQ/FroPe31C85WHOzwDpVvnLl9stVPrZbswJ6qrzOyHsDJQLZukY8Ce/6weCk+HYGmv5Ma7fuEKwxy4el7QyAx9+RxKAk6ZxnTvSqoOJkxIA6U0RDLejdu9';

    'z': fullname => 'Shodhan Sheth', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAw3Utr+dDBcNT3SRteN7KeEDiWVXy7GFuV28MrnU/fwUN8kKmx2GWbEQzw+mbqH3Dxo0dO9RqjpzYaf4PDz2JHnNca3thGOkp9UU9WVCqqppU8QrbxLEczS5hiDSDDyqQ7OBXZZS9InyPRbKjp+D6Zr+h4/dI6bBVpyAQ/9kKoxa8jbfLeeczLuqLTe0B9VnChIlhjncpybquArlCzoj5zrNdaJDAPtmDw6Zg0hnMjTaVs+9Is3FsLE/rjQChzMnJFINACkEMQCBBHWhILPaIVKYeq+lzWn47p4BY7ElBLDGGIZ4HJGsWkLlJZTpeQZEDgq7O6B4oiqmRhY59ZaK0oQ==';

    'evanb': fullname => 'Evan Bottcher', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDEEMeSZgn/K3EnRSLBhrCCBN/mDmjWBRwwtvCiKxWl1BWpAmfHGIP+DrQCwJhGtkB9+Qa1Ws6oJzTrDHZGN5rT8flsi6GTrw7bNRdUucwKBeMJwxekKk/kwGQN+WD3FF1+Y4voKuWVCvYRhIs/6qhwIR8X811VQ9jvQRU3jMaO/0hvP/uVSLLDgfY283/Lahv5GlrQVM59DoQ94PHtLXRotC/7QtXrwUQ5rIr+NmKONDu+gElh18WSGch8vqooyd8feG+L4BC1mRaghoqQeVIveS35Uki7/zkOA/J5ZhPTcVxpStaVrw2b7a5HaIo+qsodzXC0vXe+APjG/foZCUcr';

    'bamdad': fullname => 'Bamdad Dashtban', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDELn41jHZRShK2SHRD3T0JjZEitQGpRYdNyYusdLzuKS9qByNyTZ5W2/xW8MC9UTF9tsHWmU2VhZBmfCO/8qMO5TbFPBEtYkQlBiAoQ4Taqnlv1tscBL/VVuLtbEML3UHSn0Gj9H45MBbTqDKnATs2Pzo+Lu5+NP0cK23ZBHIfJJ698B0JfEq+Tu/QmYjDUWGGmXHd4evki7laFYKjXyR90U+763QPAekM9rA8Ij7jWCp6hhRWpKj6UfjKqh1afylprFbQQ358DGTmh7u804riUwPW5f+NNMCCPyEegFGqLiOj5/RaOywsY2DwjYSFTA0KIv9QMoTGP2xi9fbCu+6R', ensure => absent;

    'bdashtba': fullname => 'Bamdad Dashtban', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDELn41jHZRShK2SHRD3T0JjZEitQGpRYdNyYusdLzuKS9qByNyTZ5W2/xW8MC9UTF9tsHWmU2VhZBmfCO/8qMO5TbFPBEtYkQlBiAoQ4Taqnlv1tscBL/VVuLtbEML3UHSn0Gj9H45MBbTqDKnATs2Pzo+Lu5+NP0cK23ZBHIfJJ698B0JfEq+Tu/QmYjDUWGGmXHd4evki7laFYKjXyR90U+763QPAekM9rA8Ij7jWCp6hhRWpKj6UfjKqh1afylprFbQQ358DGTmh7u804riUwPW5f+NNMCCPyEegFGqLiOj5/RaOywsY2DwjYSFTA0KIv9QMoTGP2xi9fbCu+6R';

    'bird': fullname => 'Chris Bird', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQChol7tcer9YT9q+dwt7acDC4R9FObnEiUfhoTzvzn1jfJUZ8NwjLwNJnk8rgATJBOORH11cY13A6qvd82pDEwUERLvG/Xj8UpVka7/fYu6ZIwaHEZZxC6PcgQNzwoDSFFv4hy0RNkZPn/na1W7Oo+pWEozVw2KMNgQRvyb4Ibj+9ugVz0dgq1hK0lpumWkGL/EWbEO02xty5OKaS+sIsGUTxBkwtWFdkVaISraAtNOA84WGe21kU5gb81fFOD/w3VpO4suNMj0URxYAvnMrNMHoIE63vZaUTOKlCzSB5eEYxU2ye9gQox1Nu+sylZr+Id3LSIhZBCCBXn+9GlX912/';

    'cburgmer': fullname => 'Christoph Burgmer', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDDyRg+LYRQvV+s4nmaYfOXczV82rX9A0Dj8Eleiv7MOvPUtXUqQgpqk+MEcr69kA8kolnBTcfVdWz7jUQsYK7PKgSyYSxRpJ2JuJUjo4b0RpJ0PX/PY7HYSr0wiv369TH+Y2PN4JfeVwCfcJ6BCJbfxwCp83AarS6LsL7gJVV61lgKQG91oInOYJpY5VGC7G/8ILvDVODLEqrZv+E1bBPwtr4regNBppi5Q/tRG7pgIjMRG9CNJ/Zq4SIYQmhQW+x/RO2TRf6WwwG60I1ufpw9wPznVN7gYX2qpm3PGDfS6cq5XiS9MVPemooiGGxtQ10pHap9M1cUdj8As9K/orsn';

    'tomduckering': fullname => 'Tom Duckering', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAqvmMy6jgk6V7RVHTBjKWywXRrOyfKw18wc+vObNoYGH9bqxJpfwzUI6dJiogGz+Md9fkZuIB38FjPsu6wabgApP+fqDaMGxFsaPRXRcBJhcvTjajb+dmIrfHTIu/YR0BQCfh/0UbWCemOs+6sUsipYfw5awS3MVjCLoBrmgWKGzRaxeZ54mWwGZWg2m2DPFDOTfUHGNs4RDMsxIlvHbRGleevG1IMimhznktZNcRMjg79m7HS3tsAlaSdyWUeo8/BWBi5OLmR0uy9dSXGWlzQxP99Ft6pwnBlI1zm5rHKYW6yYslNSDDMpwVrY5fB4mkjmbakyVw6pVx0r0E0E90cw==';

    'tinygrasshopper': fullname => 'Jatin Naik', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAtBPM26vFzudNr4YNvF8gAbzdOxSQVvyfgxsind5prsSJHFHfp1IXJX3n2enqjBD1GFign1CCk+byxneMksyNakZwRlDENbZF/RkNlhrlg2mjRd/SFiFsktKX+1byv/S/4aURDq2LzA3NryNeuq/f9pW+Y1h8mLjFwn3Rpn3fOoIGe3pUhBUVe8dcgXQDsDFwENMcIOcH+M+mslUArEUKnDbDPuNJx7Osvlp1svjoCh9X8BFBN0ADa0WdhC7nOYO9NWN73sSWzHFPobicHRwmFf9TL6qlNamqKpvYbjvnn7Es/Kokxv7dGOe7nmfJ0Zow5WKhOOibmQEcfkB6+IwvTQ==';

    'cberg': fullname => 'Christian Berg', key => 'AAAAB3NzaC1kc3MAAACBAPbpbBzCjfvOb//v/ByW1O/YBhHOV8ulsTivA8FmpUuruVa/pBozEIk7HATBUT1gQWDJM7/TgFCGYyE3yEiA77tTHhQKGOVAkr6VyR6feUKPMMMlcDwdxCYRXGawokjM8uYAt35sUV0i3Q4TQf95/CbMsU6Z4A4+ZNYR+JhRBWXFAAAAFQDxIpQyyGHgUo1CS/o2DprhlJ1hTQAAAIB/av4X9z/JcvVOQviwAkORsejXhO/BL7Qq3CgaAPtdRjaeQNw3YMfWfsxqgJ0ROEQd+md0cid878SDMspeMUYyVxVROMaaK14jE9oAAHk1VVBKEHrYofU3+ETD+H77M6oSDXBprrUrRD7igRsFZGmG9glsNv3dFcokeuktX6C8WwAAAIEAxwnKMahtasZUMc16hBhV3fwd7w2gy9y3b8mLN39jT4JQzav+Y1IWGccNkaSdS0ONIrFkPR3j3YfRgcYBXAnAWUvTLnQu30Ag6g7/UFp3uscWyx5qUhcV4MkzHJEaZyZ09Tt5TEpiEmX5nVSesrj4bGRU9U+Uj+/k24YAWzCuZA0=', type => 'ssh-dss';

    'djrice': fullname => 'David Rice', key => 'AAAAB3NzaC1kc3MAAACBAISKqcTX29e+7jlD51Dz0Gt996d42WdPshkstBsmc+noWKz7mfD6fWlfQBTQom8uU9dRAeWL5SK8BSf6/zzm46clt1M84vFVItpGM5IaIlTq/NG9+PLGfQCYIC4lJABDleaSXWZ4Opvi2TR9PwEzMIsCK1uNi+/00jazGpviLNATAAAAFQDTGkSuqn6IurLTMDPNK+mhiSQTOQAAAIBoOJrFmPyaozKY/qPeJxxMynwwcwKcf9gn9MGbMnZvb+UHRXQtvFqwJ8XoNUMcFvE3D0V605i77NCz4qum03XYG5SZGaYg453Eh1O0MX/Byih0r8aVcHn4MJFgtvx4htYXl6V1BdjFCW6S6+Ev2R4AuYsTkb1+p4dN/1lxh2wFYwAAAIAeeqs7h8gVU9X6+ar3OdL1pHFrwrwLzRbQwTpn8fzcxyFJa3xkqOifkevxMBk0F2Zp7dnsQXO7gi4oT9Ib3xORp1PMTlXWv3rwyplVLaSOyfvdV7l37gUUaS9ZR63hjM1bBhSn5OEqQWFgJFChHlsy+0zYihFXGzKrjluNa1QmTg==';

    'mabis': fullname => 'Marco Abis', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDXDmkcm2Oepe5FZlf0o5Ul17SDass2mgBJNTuNpGcoiNvO5ScD+ssYDDSClhifqUojN7rjrr1HwcPgWPJQ0pTzKtZS+4ncYESspnX0/mr/Awcx9i3PV3Evwg16U1MXA/bT/wTov43HjCiy/EBXTU5sBCPC53wIW4h+xrb2ceVrREDQ0CpyNnsBaPuJ55C8ChI3AXJkFgmNrjuJAs+8md+yyLPDf6NesildNZpAbik5WRIzWB3XxqQZna0vZwWNZFZZNIJA2iJzHv9C+PGub0HF8HqZNHxhJx/t3bKdsA4H+IptLU+b21VqsA5JmA520NSbkiq4zOTS/Ff/5WySFsnv';

    'sriramgoteam': fullname => 'Sriram Narayan', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAyVrvwu5stPRvIus4qNNBmkE/O5RR9Lj9NETjm2byJfj0p06ppInm9VuOocIsAm9na7Vhv4kkte0UVhpuHfNNhwbELcCk6NhobHC64Oia1+95JDcvbSz2ggwvXp/Jv/wTZwYbaAV7/LcVlMFUszzo1Fo/Kj5lmBY8UNKxey3EaPcSuG8NN7tAysxwtxtvSHSYSdJ82wZsRHdpMCdCLE6jY/IgowfFcBZ4HfeZKlvhE+TOKACnfb8HbhCyNDywkcYuBSA1hCjkCi3crivCxQcmdxJ757uYN1SzPiK6OPijyMW4ChLVPEqqLHanik0dthnBIla6hESN5ZJlHXUYw8qMFw==';

    'ketanpkr': fullname => 'Ketan Padegaonkar', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAyBUL6IYBOakglitnGH6JUcVG1plQ9WZ6gWz68RVs2PFqNlWtTvfIuk1w2UfX8TVg/1dQhbuSLnU7JfsG2Kk05FnRb5deQwXl8vyINW71MWyWVneSajs+IuEkWjD6kxo2FSkcJS/P5FIhNMA2UjK7a9lOiKaUF16gfVXdCxkFjdnzcUbSYpK68Z0vecBqfhZgdDc2Kll6+FYsZheA5SXV5SqhOfQk2UW+XPg/TYBKwo0M3JRIlMwA6GbH+8uePB9laGnJGJhATzvwlevNhzc5F6MMfUcG9AtL8ZFzpMOKGlTUjBi+ZPKQwW7XDft9ITshSjZZlhYmHyGdTCcVLNkufQ==';

    'mcook': fullname => 'Megan Cook', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDscVtfiqkmuKxkouQTPm9d1KKGt1a89Pg6nL8GggrzzWAVRGwHYGwq1DEyJ34JCYfkcPWWnZz8pah0hvo2LIcO2kSpicvskY37+WLVDRf69DH0TAVZ6NgEFLQj3UGr5/B36sC+shkn+NMHl2nkUo+ZI8d5ELwgUa5zgAw6uyhcB40TgRJrV0//HdNrngRK6eA+52RwCue+BtnvgAK7YbpUpmPm20kH3zYwgwSj9/BQQCcREaB+oz9kFEieHm7oUKhzNeLJhCAJVtq7r2sM5+EHwH+d+JipX27nPPVCJV2F0L/I47L8v1+yL67dmkRo/bshIn9M1ELgoFDhJMSr1k1Z';

    'jlewis': fullname => 'James Lewis', key => 'AAAAB3NzaC1yc2EAAAABIwAAAgEArb4Hd0J9p+v4zLRZ48396k96ofH2yN6r2ObNAdW2IUviCZnhoEXHUx3/QEqp74ysWXTE5bpVEHI+5MUEDAhoqwM9QZykPFXuItTWbWtm6hhBof554EI5y3cFbFcfijG2/pdlFlgGH1YV3eMnwJi6h926GVzF/t/VvmG+8OBnoJxda70sTjv6ICL/ecl1nhszXPraVYLu5zTGp7A6iStn9FjgeZ3iMRUTrCrfnLlD28g/Z0uJNnzgNZtgZim5yYyriH7AxPqMgO0AIHdcjHQ445YnfAm0AZOCv+w925d0xJ/pZO0qwKLJb25Vtokhvs0Sq0DzlHvpwK/3+O73Acd/2suAxmD3NtNPxuyO6qvQ51jYYuVUkELMc4+sleA2uW6DOL/kn5PvZKXRCYwSWCHHBHz7sic2wjn2o2uGUIr/9IDr1ViTW/HP2We54O3FXtphEkq67Da5uPJzOA6+GjX49VvXELpXFejap7NaYtw+y7rSb9MQOsPZuhsYwLG+IdOYtB0zsv/KpDqsx74pq2o0DK8llsRUL6kJESq0kEEfPt375MEAhlAJxBP2ebnxSPmS5FladpKdqR5cUlQxKdsHbkxlXWVkKVypi+aRfjq/slyg9tN5nk1f4MQqZmyxWiEz8lUE7P2zX8E6J3Jo75vMIQDdX2XzRsfCcBnpAVBRNZc=';

    'russbuelt': fullname => 'Gregor Robert Russbuelt', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDBZAlCDIaRryM9YB+95jo7c6vdKOrAFHZ8Ns4zdl5S/de/glRhTik9Ont8sq9DV0NWx3g0KICulJTJhVN6Mc2bIiB2yuisSXpjnrlGQFrQay+JLVUB/IkD1DppBcG1nO9+wyLW6EfmMoLKxff1ZIEdRxvXliffzKnoU4WTeaEdnhHkbhRNXtTirx0HN7rHt5BYI3MakfK/H6x1JpZwH8vJCvXDh6II/xLzB31q7y2rhm0oFI0RYuVhPpkg9UFKKNtlNJri3CD8MZT7MSy0Leut2IneTcDwnp3MgrtwDFZ26E3mZ96a92To2Z0XbI1l5MUHiQcsnNo9cbETtIukgBMv';

    'ctford': fullname => 'Chris Ford', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQCfAIkz7zB949dZe7vKkzHngRwWq27JMfhJ5CScyQUibrL8J5/h00QUsi7opLFORQ/90SEIQDNLQGUYihAVcaMN3R3JzNsGxZClqHgl9dDhQAnWYjh881lnsMtsdQPJZEBIqLMnjXU+KD5xyrECZpOWlGwiv38tJD5OghLQQDbjM5yTgbYe2me4r9BySpH4+qBe9vP0PlZZcyACIHFhAupYgbUNrrTPKEpVc0KlPvou8IsKQ1MbJvTBpU3g3zZLyGisxuqWHRkMr5CxcebIguHQBRZT9Xp6BsWAcfDZJ1aoTM40bdMLua9eyzijQu8vZtvn+ucLiloPBMOtUbU/Zju1';

    'ebethke': fullname => 'Elke Bethke', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDfXZ+fTxFYho8xpmX16NkHXRWslkIuSkNKmXWKkTrO1EwC3C2J7y6bFqe6hrPiKomO8/4G6vC3R3JnnKYB6NNh9wsy7fSORc1WD/XvAZ/Q7Ni1XDmTZ7zogJLR0N5C8cciSbQPvYNaUhcZH1N2AXCdMODR3dCc+jAcz0U1U2S/Rw1WlI9tEvqmqImfRh0mSCFeCL2ehts1SA3UToNpqJb+wdNROCvYwoKF5GNBd02Ib0zvPSWSauEdaJ4n2Tus/dBvImsS0B1/ck8SUEOs0k9Z+Sj10oH7Q57RLa7pEodyeDJaJdLWH1VBK0XzXjkYC/WX2xNAht+aGf0kLPmxfEST';

    'cvillela': fullname => 'Carlos Villela', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQC5hlIKsua3ibv5I9XTHYm7zG+HDU+Z2foZPaxDgtEGGtFoQQ6HhSfhY7B9rGBO3fyibLsPi2pieYWg5gzetLgUYUu/nDmA7uFZNObtWGivdgMO3nDRisKgVUA7w4W8ZAXtZy5Ypsr4P1D9wnXZ5SpsWDv/gt4sExfYkr9dpxUaAnu7ql0vMSM/cdJgjLhyg5mO0CdpOo45w7yEl3L04wNxi87GxTd/aSF9Fh3e6DPwvYRbPZ2WrMI1w9LlsoqvXcBmshE0Y37KszP3Dbt0XcRyJEepd/ZdW1AP52Uynh3g6bbZNZNDbsF45b8e7wmOt4VvVNc3FHTFlVau+818R+Kd';

    'arvindsv': fullname => 'Aravind SV', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDLD83q9DQ/7ss2iIkoENv/pCmHT7AQJnOiUQHmMOid5Rl5bms2ISSF68Tvu9WOrAThWpY/AFOWttqE9zS9/Urpr9yeVsfh+0oY0PV1GriGo9AmgRkK+YKtQp+NWLaZ7BNt0huo5rm6sRJKqi335YXZdhJLosX8bA8lMmxklxn1zLPQxNBK1iTPAxa+kcM14pG6tr/Yck0LxuTsg+aksFQaiP1pIvIv4Z/L8RBi47hydxMeHKMNNn/JUsyVguVgjcOha+de/zS2NMNxeUYXkkySCIxM4tumAczAzITepEWTVlR+hcsGgkSNTEOfmJlv+zdjby62oSQXIMbQbiST87rX';

    'rsakalle': fullname => 'Ranjan Sakalley', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQC/fGdzsLBhteHLS7AfDU0UcdYuW9h+wUClVnYkd7hOPU2pYz4JFjAWJ5YEPdVc6T7eqJWOFv73XRfhFXVapgSv7YgBalDQaquId/hrPwZ5s/6Gbaf7Z0YC83JJh3NeWvophF+eBHdEedhgNN1OwdQQL+4B1JRU0s1ZXZGAw229i7lXiYjR/iB1/1iJb21xo70ueRW+qK6mPaLj145fCIvXXixnyarmuG41C338tkQKbBYwepztwuLCjLIAuu5vVLWHr3AiK22A5HKrxY3J+Zqnu59HQT/k8CFL1lxVEoi3SzLImDQOJtfR39eSyPHRwho/AjrgBeQ4LboykOCA5c35';

  'jsingh': fullname => 'Jyoti Singh', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDdRxZKQoURYc3G7HELubxY5dxAEjJgDClGDt68ssdmJutGnc7EEtjP50O8QqU7AB+sEy3IBFqslQ3Qom/pQ1FTzizkkJRAMwN168GAnG/aVKFjld6HyV8M5Aw7SiCKhiOSVHX5Yo6HDcA0IDL+Ldv6r+RrZ7s1Ctg1WgCA1S39P8FOZQ8+UKFDYsI4ycF7X2Bugj6qe/41EDaiHnanBF3tS+6VR9FMgGDllLTDpuovi9YQ2Z97/Rb9V/YfCLNGevWvNlDM0gHb31fvv+tjncZvAEi/FlZ63Bz2K5ZpLjZgEjn38Ncc82OSMUoeZbvgbV4XYJG58nb71MkW3TdcFCgn';

  'dchetwyn': fullname => 'Daley Chetwynd', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQDOlVGDseV5YQK5329jP/yKi2O8TWlLwPevjC8jUtMP1RgaVTfiqVRw7Da07pIHiSnFTHnXwcCGt/zU2DJ6DIirMZ4SgLzgRkR5FUycQblqTJz0+/mlkcIBjXopPh7H6Te4b9DSRfjzXqf9WvjAud6reEpWyoFRGkRokEIA04Ga1QQtzlsTuGDA2r/zloPfent/9CiPH+x8CS4RMdzP2H5QPl/xA20Qh9aoDkavtwvYTV2mAdSWpwx29yZmRl6XOj3ad5SfH3yQ/p2YVJI9JWEh/xvxjgB1xB5Z+KlPakCHzeYXbEjAXdhTc9xL53+LlrZq6k6OV0tfRYO04j99lK2z';

    'bguthrie': fullname => 'Brian Guthrie', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAqv30Clr/UEnI2v8rYXg+WoqLS3UP50MEOqGoBjwAsjU4zj3YkmyusO76mL5GniuUlCxC/N+rVvMVdKuw5sKNR5LVNniqr5fEIcRgYry0xBqAmd/1w0Kkv4zFfMO9IAq7Vnyq7HUT8CUbT3Jnq4vrg8rybxScF5mVG229tyU5PDY0vno3CqXEbTS0oegqWX6iLej2deqbUCLqBucuGrvW1PLuA2vdjhygH9uVK+ZkeygQHA1fgDvGbeX9zGF0M3s6PfsUVfLQSbs7u8jhVlqZAh9TgQPEsmbIXiuJ/rEIVppO2mYUPzUkO0nLIez2DdeXYkaBqbX33bWNHjDNLdvN3w';

    'sriki': fullname => 'Srikanth Seshadri', key => 'AAAAB3NzaC1yc2EAAAADAQABAAABAQCnLmyc784Q7YpRRbt+cqRNyHt6h4dfTFh4NU+RTZqPf/wRSHPoXaVdG1HruthlEniP6sEhW20g2BnlwDPNlRl8+o58vp/1CWKeEso3aveOi0qp4sTyya8beVStfgtaHav4JlgOVYF3cnBF3hYZjO1LTcMIlwveAEHFxdlj7OJkpA6/egGAjoCTJSoQpRBQr+GreCWcVfpkW7nNF05ROCTPAKZQxWFMQLVENzQdJeI9GUUUVVWTNFZszRbPbtOczPizA/njjsILsIDqFxUK+9gzo91mS3WXWQBbsLWVWVTha6U84zJX7/meveeJEOwB/+BrmW7clrRVqnQvqOdZgB6N';

    'ascott': fullname => 'Adam Scott', key => 'AAAAB3NzaC1yc2EAAAABIwAAAQEAtkZabTxGhsgNIf8M9vqFuzdeQGRfuhBe/5snMZ7dLtDnYlvNWlElom8JJkTcPz0KtI6homDeZJZlqtTnq2Ruonr70c29pu9C8HnVKITLj50zWhfKEtOVxPlyiysR5jibVJfkIXnl1eDk0yrlGg/jToOAkaWciZ4VGQJqKiY9DMGQ0Ju9+XbvpGKqeiWnKwlqZfriQ5MO+pEgaVfk29g236mxiy2KhQVfXObcEtp61aSd32u4Nwy4oNv4rf8sdtdyoWt5cdQ8Bb71amb8THifRomG0K/vP8fCYUPhtHchVeedrrgXgxvmB3HB10yRxj9rJB195FAgUzVZ2CPNc0A2aQ==';

    'jarnold': fullname => 'Jim Arnold', key =>'AAAAB3NzaC1yc2EAAAADAQABAAABAQCYT1SS8WrU4jJ6FZX/Sl5FfB4mRC3nc6m3PdGY+uQU93zLjxaq4nP302S+Zpc4lPM/bR8T+PWs4RzjFvOg8WnOaGQRrXhu7Ip2OYy/ixWSv430Zj5jxf/kXYZkjmqZC4LO12JjgZ3TcEMhJSgUzdH/UVDk1T2xGyfI0jW7LAEg9nEL7XJ6avoH/C+MWJpgXWqfLOuvATQYfK3GSnIU+i7yuTyaMbkOHDuTNlVMebRPFIcpsDwphl8X65M9C8wj1WBt99lIIP4STpoybKhp8/8/9F8hj0rS92ZtwuzhMxPdaAY19Z9m4KAQkZxLs3r2r+GaUqWc2GTpie/HwUOHEMWp';
  }
}
define users::irc($key, $fullname, $ensure = 'present', $username = $title, $type = 'rsa') {
  if 'present' == $ensure {
    $dir_ensure  = 'directory'
    $file_ensure = 'file'
  } else {
    $dir_ensure  = 'absent'
    $file_ensure = 'absent'
  }

  user { "$username":
    ensure     => $ensure,
    comment    => $fullname,
    managehome => true,
    shell      => '/usr/bin/irssi',
    require    => Class['irssi'],
  }

  file { "${username}_irssi_dir":
    ensure  => $dir_ensure,
    path    => "/home/${username}/.irssi",
    mode    => '0755',
    owner   => $username,
    group   => $username,
    require => User[$username],
  }

  file { "${username}_irssi_config":
    ensure  => $file_ensure,
    path    => "/home/${username}/.irssi/config",
    content => template('users/irssi_config'),
    require => File["${username}_irssi_dir"],
  }

  ssh_authorized_key { "${username}_key":
    ensure  => $ensure,
    key     => $key,
    type    => $type,
    user    => $username,
    require => User[$username],
  }
}