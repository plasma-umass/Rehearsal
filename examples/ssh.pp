# This manifest manages SSH authorized keys using both the ssh_authorized_key
# type and by overwriting the ~/.ssh/authorized_keys file. The right solution is
# to either one resource type and not both. Rehearsal detects the error,
# although it doesn't suggest the best solution.

file {'/home/arjun/.ssh':
  ensure => directory,
}

ssh_authorized_key{'arjun@local':
  user => 'arjun',
  type => 'ssh-rsa',
  key  => 'foobar',
  ensure => present,
  require => File['/home/arjun/.ssh']
}

file{'/home/arjun/.ssh/authorized_keys':
  content => "ssh-rsa foobar",
  ensure => present,
  require => File['/home/arjun/.ssh']
}