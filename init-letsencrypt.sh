#!/bin/bash

DOMAIN="hearu.p-e.kr"
EMAIL="kimyongjun0129@naver.com"  # 이메일 주소로 변경하세요

# 임시 인증서 생성 (nginx 초기 구동용)
mkdir -p ./certbot/conf/live/$DOMAIN
openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
  -keyout ./certbot/conf/live/$DOMAIN/privkey.pem \
  -out ./certbot/conf/live/$DOMAIN/fullchain.pem \
  -subj "/CN=localhost"

# nginx 시작
docker compose -f docker-compose.prod.yml up -d nginx

# 임시 인증서 삭제
rm -rf ./certbot/conf/live

# Let's Encrypt 인증서 발급
docker compose -f docker-compose.prod.yml run --rm certbot certonly \
  --webroot \
  --webroot-path=/var/www/certbot \
  --email $EMAIL \
  --agree-tos \
  --no-eff-email \
  -d $DOMAIN

# nginx 재로드
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload

echo "SSL 인증서 발급 완료"
