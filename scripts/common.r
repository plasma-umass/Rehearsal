library("ggplot2")
library(sitools)
library(scales)
library(plyr)
library(dplyr)
library(grid)
library(fontcm)
library(extrafont)

linew <- 0.9
mytheme <- function() {
  return(theme_bw() +
    theme(
      panel.background = element_rect(size = 0.9),
      text = element_text(family = "CM Roman", size=12),
      panel.grid.major = element_blank(),
      panel.grid.minor = element_blank(),
      axis.ticks = element_line(size=0.05),
      axis.ticks.length=unit("-0.05", "in"),
      axis.ticks.margin=unit("0.1", "in"),
      legend.key = element_rect(colour=NA),
      legend.margin = unit(0.001, "in"),
      legend.key.size = unit(0.2, "in"),
      legend.title = element_blank(),
      legend.text = element_text(family = "CM Roman", size=8),
      legend.background = element_blank()))
}

mysave <- function(filename, plot) {
  ggsave(filename, plot, width=4, height=3, units=c("in"))
  embed_fonts(filename)
}
