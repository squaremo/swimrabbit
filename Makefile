all: js.jar
	javac -cp js.jar:rabbitmq-client.jar *.java

js.jar:
	@echo "Download js.jar from http://www.mozilla.org/rhino/download.html"
	false

rabbitmq-client.jar:
	@echo "Download rabbitmq-client.jar from http://www.rabbitmq.com/"
	false
