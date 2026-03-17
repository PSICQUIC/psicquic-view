FROM tomcat:9-jdk11

ENV USER=docker
### mi user
ENV UID=7610
### molint group
ENV GID=1489
RUN addgroup --gid "$GID" "$USER" \
  && adduser \
  --disabled-password \
  --gecos "" \
  --home "$(pwd)" \
  --ingroup "$USER" \
  --no-create-home \
  --uid "$UID" \
  "$USER"

ADD /war_files/psicquic-view.war "/usr/local/tomcat/webapps/Tools#webservices#psicquic#view.war"
ADD /war_files/imex-view.war "/usr/local/tomcat/webapps/intact#imex.war"
RUN cp -r webapps.dist/ROOT webapps/
RUN cp -r webapps.dist/manager webapps/

ADD /config/context.xml "/usr/local/tomcat/conf/context.xml"

RUN mkdir /clustering
RUN mkdir /clustering/psicquic-cache
RUN mkdir /clustering/imex-cache

RUN chown -R $USER:$USER /usr/local/tomcat
RUN chown -R $USER:$USER /clustering

CMD ["catalina.sh", "run"]