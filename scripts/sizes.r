source("common.r")

df <- read.csv("../results/sizes.csv")
df <- rbind(transform(df, "Name" = Name, "Paths" = Before, "Pruned" = "No"),
            transform(df, "Name" = Name, "Paths" = After, "Pruned" = "Yes"))

plot <- ggplot(df, aes(x=Name,y=Paths,fill=Pruned)) +
  mytheme() +
  scale_fill_manual(values=c("red", "blue")) +
  theme(legend.title = element_text(size = 8),
        legend.position = c(0.85, 0.8),
        axis.text.x=element_text(angle=50, vjust=0.5)) +
  geom_bar(stat="identity",position="dodge") +
  labs(x = "Benchmark", y = "Paths")
mysave("../results/sizes.pdf", plot)

