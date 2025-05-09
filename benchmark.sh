if [ $# -lt 1 ]; then
    echo "set a title"
    exit 1
fi

echo "$1" >> benchmarks.log
mvn clean package -X && mvn azure-functions:run >> benchmarks.log
