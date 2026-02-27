FROM tomcat:9-jdk11

ENV USER=docker
### psi_ia user
ENV UID=2799
### pst_pub group
ENV GID=1137
RUN addgroup --gid "$GID" "$USER" \
  && adduser \
  --disabled-password \
  --gecos "" \
  --home "$(pwd)" \
  --ingroup "$USER" \
  --no-create-home \
  --uid "$UID" \
  "$USER"

ADD /war_files/psicquic-view.war "/usr/local/tomcat/webapps/psicquic#view.war"
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