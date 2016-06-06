source("common.r")

df <- read.csv("../results/determinism.csv")
df <- df %>% filter(Pruning == "false") %>% select(-Pruning)
maxTime <- max(df %>% select(Time) %>% filter(!((Time == "memout") | (Time == "timeout"))) %>% transform(Time = strtoi(Time)))
outValue <-((maxTime %/% 30) + 600) * 30

df <- df %>%
  transform(Time = ifelse(Time == "memout" | Time == "timeout", outValue,strtoi(Time)) / 1000) %>%
  transform(Commutativity = gsub("true", "Yes", gsub("false", "No", Commutativity)))

range <- seq(0, max(df %>% select(Time)), by=30)
ticks <- range
ticks[length(ticks)] <- "Timeout"

df <- ddply(df, c("Name", "Commutativity"), summarise,
            Mean = mean(Time),
            Trials = length(Time),
            Sd = sd(Time),
            Se = Sd / sqrt(Trials))

plot <- ggplot(df, aes(x=Name,y=Mean,fill=Commutativity)) +
  scale_fill_manual(values=c("red", "blue")) +
  mytheme() +
  theme(legend.title = element_text(size = 8),
        legend.position = c(0.87, 0.8),
        axis.text.x=element_text(angle=50, vjust=0.5)) +
  geom_bar(stat="identity",position="dodge") +
  geom_errorbar(aes(ymin=Mean-Se,ymax=Mean+Se,width=.4), position=position_dodge(0.9)) +
  scale_y_continuous(breaks = range, labels = ticks) +
  labs(x = "Benchmark", y = "Time (s)")

mysave("../results/commutativity.pdf", plot)
