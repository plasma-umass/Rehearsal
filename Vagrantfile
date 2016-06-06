$script = <<EOF
apt-get update -q
apt-get install -yq software-properties-common
add-apt-repository ppa:webupd8team/java
echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
echo "deb http://cran.rstudio.com/bin/linux/ubuntu trusty/" | tee -a /etc/apt/sources.list.d/r.list

apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823
gpg --keyserver keyserver.ubuntu.com --recv-key E084DAB9
gpg -a --export E084DAB9 | sudo apt-key add -

apt-get update -q

echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
apt-get install -yq oracle-java8-installer sbt unzip r-base ghostscript

wget -q http://www.scala-lang.org/files/archive/scala-2.11.7.deb
dpkg -i scala-2.11.7.deb
rm scala-2.11.7.deb
EOF

$userscript = <<EOF
cd /home/vagrant

ZIPFILE=z3-4.4.1-x64-ubuntu-14.04.zip
URL=https://github.com/Z3Prover/z3/releases/download/z3-4.4.1/$ZIPFILE
wget $URL
unzip $ZIPFILE
mv z3-4.4.1-x64-ubuntu-14.04 z3
echo 'PATH=/home/vagrant/z3/bin:$PATH' >> .profile

mkdir -p /home/vagrant/R/x86_64-pc-linux-gnu-library/3.2
R -e 'install.packages("ggplot2", repos="http://cran.us.r-project.org")'
R -e 'install.packages("sitools", repos="http://cran.us.r-project.org")'
R -e 'install.packages("scales", repos="http://cran.us.r-project.org")'
R -e 'install.packages("plyr", repos="http://cran.us.r-project.org")'
R -e 'install.packages("dplyr", repos="http://cran.us.r-project.org")'
R -e 'install.packages("grid", repos="http://cran.us.r-project.org")'
R -e 'install.packages("fontcm", repos="http://cran.us.r-project.org")'
R -e 'install.packages("extrafont", repos="http://cran.us.r-project.org")'
R -e 'library(extrafont); font_install("fontcm")'
EOF

Vagrant.configure("2") do |config|

  config.vm.box = "ubuntu/trusty64"
  config.ssh.forward_x11 = false
  config.vm.synced_folder ".", "/vagrant"
  config.vm.provision "shell", inline: $script, privileged: true
  config.vm.provision "shell", inline: $userscript, privileged: false

  config.vm.provider :virtualbox do |vb|
    vb.gui = false
    vb.customize ["modifyvm", :id, "--memory", "4096"]
  end

end
