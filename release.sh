set -e # fail script on error

if [[ -z "$1" || -z "$2" ]]
then
  echo "version mut be provided"
  exit -1;
fi

releaseVersion=$1
nextVersion=$2-SNAPSHOT

echo "release" $releaseVersion ", next" $nextVersion

mvn --batch-mode -Dtag=$releaseVersion release:prepare \
                 -DreleaseVersion=$releaseVersion \
                 -DdevelopmentVersion=$nextVersion

mvn release:clean
git pull

git checkout $releaseVersion
docker build -t nbittich/api-gateway:v$releaseVersion .
docker tag nbittich/api-gateway:v$releaseVersion nbittich/api-gateway:v$releaseVersion
docker push nbittich/api-gateway:v$releaseVersion

git checkout main