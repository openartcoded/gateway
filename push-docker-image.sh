docker build -t artcoded/api-gateway .
docker tag artcoded/api-gateway artcoded:5000/artcoded/api-gateway
docker push artcoded:5000/artcoded/api-gateway
