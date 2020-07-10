#!/bin/sh

work_path=$(dirname $(readlink -f $0))
cd ${work_path}
env hy fund_info.hy
