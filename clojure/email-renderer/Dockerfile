FROM clojure:temurin-21-tools-deps
WORKDIR /app
COPY deps.edn .
COPY src ./src
ENV PORT=3000
EXPOSE 3000
CMD ["clojure", "-M:run"]
