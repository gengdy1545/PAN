# PAN - Arxiv Summary Mailer

**PAN (Paper is All you need)** is a robust, automated tool designed to keep researchers and enthusiasts up-to-date with the latest academic papers.
It periodically crawls Arxiv for new submissions, generates concise, beginner-friendly summaries using Google's Gemini 1.5 Flash model, and delivers a daily digest directly to your inbox.

## 🛠️ Prerequisites
* **Java 17** or later
* **Maven** 3.6+
* **Google Gemini API Key**
* **SMTP Email Account** (QQ Mail)

## 🚀 Installation

The system requires a dedicated directory for operation.

1. Setup Directory
```bash
mkdir -p ~/opt/arxiv_mailer
echo 'export PAN_HOME=$HOME/opt/arxiv_mailer' >> ~/.bashrc
source ~/.bashrc
```

2. Build & Install: Run the provided installation script from the project root directory.

```bashbash
./install.sh
```

## ⚙️ Configuration

### Configuration File

After installation, edit the configuration file at `$PAN_HOME/etc/pan.properties`:

```properties
# --- Execution Mode ---
# 'daemon': Internal scheduler (Local)
# 'oneshot': Run once and exit (Cloud/Docker)
pan.mode=daemon

# --- Email Settings (Example: QQ Mail) ---
spring.mail.host=smtp.qq.com
spring.mail.port=465
spring.mail.username=your_email@qq.com
spring.mail.password=your_smtp_auth_code
mailer.sender=your_email@qq.com
mailer.recipients=subscriber1@example.com, subscriber2@example.com

# --- Google Gemini AI ---
gemini.api-key=YOUR_GEMINI_API_KEY
gemini.model-name=gemini-1.5-flash

# --- Arxiv Settings ---
# Comma-separated categories (e.g., cs.AI, cs.LG, cs.CV)
arxiv.categories=cs.DB

# --- Scheduler (Daemon Mode Only) ---
# Cron expression: Seconds Minutes Hours Day Month Week
# Default: 10:00 AM (Asia/Shanghai) on Weekdays
pan.schedule.cron=0 0 10 ? * MON-FRI
```
## 💻 Usage

In local **deamon** mode, the application stays running and triggers the task automatically according to pan.schedule.cron.

```bash
# Start in foreground
$PAN_HOME/bin/start.sh

# Start in background (Recommended)
nohup $PAN_HOME/bin/start.sh > /dev/null 2>&1 &
```

**Logs**: Check $PAN_HOME/log/pan.log for output.

## ❓ Troubleshooting
**Q: The crawler returns 0 papers.** A: Arxiv does not update on weekends (Friday/Saturday EST). Ensure your system time and timezone are correct. The crawler uses America/New_York time internally to match Arxiv's schedule.

**Q: How do I use a Proxy locally?** A: Edit script/start.sh and add your proxy configuration to JAVA_OPTS:
```bash
JAVA_OPTS="... -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
```
