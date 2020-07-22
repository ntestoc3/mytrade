#!/bin/sh

work_path=$(dirname $(readlink -f $0))
rm -rf ${work_path}/../public/js/cljs-runtime/
scp ${work_path}/../public/index.html server:/data/funds/datas/
scp -r ${work_path}/../public/js/ server:/data/funds/datas/
scp -r ${work_path}/../public/css/ server:/data/funds/datas/
