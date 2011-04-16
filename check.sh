#!/bin/sh
CIPHER_LIST=`openssl ciphers | tr ":" " "` > /dev/null 2>&1
killall openssl

IP=`ifconfig  | grep 'inet addr:'| grep -v '127.0.0.1' | cut -d: -f2 | awk '{ print $1}'`
START_PORT=22222

openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout server.key -out server.crt -subj '/C=US/ST=New York/L=New York/CN=browsercheck.local' > /dev/null 2>&1

echo "<html><body>" > test.html
PORT=$START_PORT
for PROTOCOL in ssl2 ssl3 tls1; do
    for CIPHER in $CIPHER_LIST; do
	openssl s_server -cert server.crt -key server.key -accept $PORT -www -$PROTOCOL -cipher $CIPHER & > /dev/null 2>&1
        echo "<h4>Protocol [$PROTOCOL], Cipher=[$CIPHER], Port=$PORT</h4>" >> test.html
        echo "<iframe src=\"https://browsercheck.local:$PORT\"></iframe>" >> test.html
	let PORT++
    done
done
echo "</body></html>" >> test.html

openssl s_server -cert server.crt -key server.key -accept $PORT -WWW & > /dev/null 2>&1
echo "Open https://$IP:$PORT/test.html"
echo "To kill servers, run 'killall openssl'"