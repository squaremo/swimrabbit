all: js.jar
	javac -cp js.jar *.java

js.jar:
	@echo "Download js.jar from http://www.mozilla.org/rhino/download.html"
	false
