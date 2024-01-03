# b445e34b5f9006ac4169318b3bef75fe10cf6e17
# Even faster flatten-seqs

create-rows 69.2
replace-all-rows 133.7
partial-update 58.6
select-row 25.5
swap-rows 58.3
remove-row 31.5
create-many-rows 604.1
append-rows-to-large-table 83.9
clear-rows 21.8

# 36d7ee04dbfef171ffb3ac6b90c408edb643195c

create-rows
replace-all-rows
partial-update
select-row
swap-rows
remove-row
create-many-rows
append-rows-to-large-table
clear-rows

# fa004953a392260814044411cf2664a7d23c7a2b
# Keep fully realized vdom around between renders

create-rows 65.8
replace-all-rows 154.2
partial-update 50.8
select-row 21.3
swap-rows 48.4
remove-row 29.4
create-many-rows 620.9
append-rows-to-large-table 93
clear-rows 17.1

# dd5b2cb98ac25f9429090dc3a2c17b7d70545f4f
# Do not "parse" hiccup nodes known to not have changed

create-rows 75.1
replace-all-rows 156.8
partial-update 50.6
select-row 23.2
swap-rows 55.5
remove-row 31.5
create-many-rows 680.4
append-rows-to-large-table 96.9
clear-rows 25.7

# dda4f5d96c2d49504e5564f907757d9416a3b7f9 (annen branch)

create-rows 82.6
replace-all-rows 96.9
partial-update 671.
select-row 31.5
swap-rows 59.2
remove-row 33.8
create-many-rows 687.3
append-rows-to-large-table 101.9
clear-rows 19.6

# 31acdf3e3931ce892456f7d6e7bfb35250e4d37f

create-rows 74.8
replace-all-rows 154
partial-update 54.6
select-row 22.6
swap-rows 51.3
remove-row 30.2
create-many-rows 652.2
append-rows-to-large-table 95
clear-rows 19.3

# 7b2980cbcf8daa3dad4088f6b10f5819fad5eb53
# Only move keyed nodes (without "Do not "parse" hiccup nodes known to not have changed")

create-rows 73
replace-all-rows 155
partial-update 54.2
select-row 21.2
swap-rows 50.9
remove-row 28.2
create-many-rows 628
append-rows-to-large-table 91
clear-rows 18.2

# 34ff8250d36ceaf346c236ad9a2f6271ab2947cd
# Native hiccup headers

create-rows 75.5 +
replace-all-rows 86.6 +
partial-update 46.4 +
select-row 19.8 +
swap-rows 47 ---
remove-row 26 ---
create-many-rows 615.1 +
append-rows-to-large-table 89.7 +
clear-rows 16.6 +

# 0807916815342778cb6fb71af3ce38250a6557de
# Native vdom

create-rows 73.5 +
replace-all-rows 85.7 +
partial-update 46 +
select-row 21.1 +
swap-rows 48.7 +
remove-row 26.8 +
create-many-rows 629.4 +
append-rows-to-large-table 90.6 +
clear-rows 16.4 +

# be4204fd880d75beb75a0568d5984aa8beaa358e
# Native vdom - objects

create-rows 73.9 +
replace-all-rows 85.4 +
partial-update 45.3 +
select-row 21.1 +
swap-rows 48.4 +
remove-row 26.1 +
create-many-rows 623.4 +
append-rows-to-large-table 91.2 /
clear-rows 16.2 +

# 18c895d7aed54b930f9616b8828ccaddfc0c210a
# Native key indices

NB! Must require string keys, if this is to be used

create-rows 74.8 +
replace-all-rows 86 +
partial-update 44.8 +
select-row 18.6 +
swap-rows 46.7 +
remove-row 25.6 +
create-many-rows 613.6 +
append-rows-to-large-table 90.2 +
clear-rows 16.3 +

# 539e272a48a8e3766ab86666add4699052b5c6e0
# Native vdom + native indices

create-rows 73 +
replace-all-rows 84.1 +
partial-update 45.2 +
select-row 18.3 +
swap-rows 46.8 +
remove-row 25.4 +
create-many-rows 622.6 +
append-rows-to-large-table 89.2 +
clear-rows 16.2 +

## bd6514645c3bdbbf0031631a0387082ed6ab8933
## Native vdom + native hiccup headers

create-rows 76
replace-all-rows 86.6
partial-update 45.5
select-row 19.7
swap-rows 47
remove-row 26
create-many-rows 613.5
append-rows-to-large-table 90
clear-rows 16.4


# vs React

create rows 45.5 75.1 1.65
replace all rows 49.2 87.5 1.77
partial update 21.9 45.7 2.08
select row 4.7 19.9 4.23
swap rows 161.0 47.3 0.29
remove row 17.3 26.0 1.50
create many rows 554.3 620.1 1.11
append rows to large table 49.4 90.2 1.82
clear rows 14.3 16.3 1.13

create rows 45.5 75.5 1.65
replace all rows 49.2 87.7 1.78
partial update 21.9 44.2 2.01
select row 4.7 19.9 4.23
swap rows 161.0 46.4 0.28
remove row 17.3 25.7 1.48
create many rows 554.3 623.1 1.12
append rows to large table 49.4 91.8 1.85
clear rows 14.3 16.3 1.13
