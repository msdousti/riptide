#!/bin/sh -ex

for graph in routing-tree; do
    http --check-status "http://yuml.me/diagram/scruffy/class/$(paste -s -d',' docs/$graph.yuml.txt)" > $(dirname $0)/$graph.png
done