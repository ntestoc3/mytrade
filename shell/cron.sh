#!/bin/sh

work_path=$(dirname $(readlink -f $0))
cd ${work_path}
/usr/local/bin/hy fund_info.hy
