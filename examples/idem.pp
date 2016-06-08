file{"/dst.txt":
  source => "/src.txt"
}

file{"/src.txt":
  ensure => absent,
  require => File["/dst.txt"]
}