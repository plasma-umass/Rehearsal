# This is a somewhat contrived example of a non-idempotent manifest.

file{"/dst.txt":
  source => "/src.txt"
}

file{"/src.txt":
  ensure => absent,
  require => File["/dst.txt"]
}