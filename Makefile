# -*- mode: Makefile -*-
#
# Copyright (c) 2013 Anil Madhavapeddy <anil@recoil.org>
# Copyright (c) 2013 Richard Mortier <mort@cantab.net>
#
# Permission to use, copy, modify, and distribute this software for any purpose
# with or without fee is hereby granted, provided that the above copyright
# notice and this permission notice appear in all copies.
#
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
# REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
# AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
# INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
# LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
# OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
# PERFORMANCE OF THIS SOFTWARE.
#

# XENIMG: the name to provide for the xen image and configuration
XENIMG ?= www

# FLAGS for the "mirage configure" command.
FLAGS  ?=

MODE ?= unix

NET ?= socket


.PHONY: all configure depend build run clean

all:
	@echo "You can then build the mirage application in the src/ directory"
	@echo "cd src && mirage configure && make"
	@echo "For unikernel configuration option, do \"mirage configure --help\" in src/"

configure:
	cd src && NET=$(NET) mirage configure $(FLAGS) -t $(MODE)

depend:
	cd src && make depend

build:
	cd src && make build

run:
	cd src && ./mir-nethsm

test:
	src/mir-nethsm & cd tests/end-to-end && sbt test ; sleep 1 ; kill $$!

clean:
	cd src && make clean ; mirage clean
