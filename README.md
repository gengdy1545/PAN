# ArxivSummaryMailer

This system periodically crawls the latest paper abstracts from Arxiv, 
generates concise summaries using the Google Gemini model, and pushes them to subscribers via email.

## 1. Environment Setup

This system is built and managed using Java and Maven.

```bash
sudo apt update
sudo apt install openjdk-17-jdk
sudo apt install maven # or install manually
```

The system requires a dedicated installation directory for setup and operation.

```bash
echo 'export PAN_HOME=$HOME/opt/arxiv_mailer/' >> ~/.bashrc
source ~/.bashrc
```
