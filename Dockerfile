FROM jetty:9.4.7-jre8
EXPOSE 8080
COPY target/dplava-github-validator.war /var/lib/jetty/webapps/ROOT.war
