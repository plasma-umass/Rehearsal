trait InlineTestSuite extends org.scalatest.FunSuite {

  import rehearsal._
  import PuppetSyntax.{FileScriptGraph, ResourceGraph}

  def genericTestRunner(resourceGraph: ResourceGraph,
                        fileScriptGraph: FileScriptGraph): Unit

  private def runTest(name: String, program: String): Unit = {
    test (name) {
      val resourceGraph = PuppetParser.parse(program).eval().resourceGraph()
      val fileScriptGraph = resourceGraph.fsGraph("ubuntu-trusty")
      genericTestRunner(resourceGraph, fileScriptGraph)
    }
  }


  runTest("single package without attributes",
          """package{"sl": }""")

  runTest("one directory", """
      file{"/a": ensure => directory }
    """)

  runTest("file without ensure with content should succeed", """file{"/foo":
                       content => "some contents"
                     }""")

  runTest("single puppet file resource",
          """file{"/foo": ensure => present }""")

  runTest("single directory", """
          file{"/tmp":
            ensure => directory
          }""")

  runTest("file inside a directory", """
           file{"/tmp/foo":
             ensure => present,
             require => File['/tmp']
           }
           file{"/tmp":
             ensure => directory
           }""")

  runTest("single puppet file resource with force",
          """file{"/foo":
               ensure => file,
               force => true
             }""")

  runTest("delete file resource",
          """file{"/foo": ensure => absent }""")

  runTest("delete dir with force",
          """file {"/tmp":
                   ensure => absent,
                   force => true
                  }""")

  runTest("2 package dependent install", """
           package{"sl": }
           package{"cmatrix":
                    require => Package['sl']
                  }""")

  runTest("2 package concurrent install",
          """package{["cmatrix", "telnet"]: }""")

  runTest("single package remove", """
           package{"telnet":
             ensure => absent
           }""")

  runTest("3 packages install",
          """package{["sl", "cowsay", "cmatrix"]: }""")

 runTest("single group creation",
         """group{"thegroup": ensure => present }""")

  runTest("single user creation",
          """user{"abc": ensure => present }""")

  runTest("concurrent group creation",
          """group{["a", "b"]: ensure => present }""")

  runTest("concurrent user creation",
          """user{["abc", "def"]: ensure => present }""")

  runTest("user remove", """user{"abc": ensure => absent }""")

  runTest("two directories (missing dependency)", """
      file{"/foo": ensure => directory}
      file{"/foo/bar": ensure => directory}
      """)

}
