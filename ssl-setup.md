# SSL Certificate Setup for docent.rongrong.org

## Step 1: Create Cloudflare Origin Certificate

1. Go to Cloudflare Dashboard → SSL/TLS → Origin Server
2. Click "Create Certificate"
3. Settings:
   - Generate private key and CSR with Cloudflare
   - Hostnames: `docent.rongrong.org`
   - Certificate Validity: 15 years
4. Click "Create"
5. **IMPORTANT**: Copy both the Origin Certificate and Private Key immediately (private key won't be shown again!)

## Step 2: Save Certificate Files

Create the certificate file:
```bash
sudo nano /etc/ssl/certs/docent.rongrong.org.pem
# Paste the Origin Certificate (starts with -----BEGIN CERTIFICATE-----)
```

Create the private key file:
```bash
sudo nano /etc/ssl/private/docent.rongrong.org.key
# Paste the Private Key (starts with -----BEGIN PRIVATE KEY-----)
sudo chmod 600 /etc/ssl/private/docent.rongrong.org.key
```

## Step 3: Test and Reload Nginx

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## Step 4: Start the Proxy Server

```bash
cd /var/www/docent
python3 proxy_server.py &
# Or use systemd service (see below)
```

## Step 5: Verify Cloudflare DNS

Make sure `docent.rongrong.org` A record points to this server's IP with Cloudflare proxy enabled (orange cloud).

## Systemd Service for Proxy Server

Create `/etc/systemd/system/docent-proxy.service`:
```ini
[Unit]
Description=Docent AMR Proxy Server
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/var/www/docent
ExecStart=/usr/bin/python3 /var/www/docent/proxy_server.py
Restart=always
RestartSec=5
Environment=AMR_IP=192.168.219.42

[Install]
WantedBy=multi-user.target
```

Then enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable docent-proxy
sudo systemctl start docent-proxy
```
