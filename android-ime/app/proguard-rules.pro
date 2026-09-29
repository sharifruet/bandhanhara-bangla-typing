# Bandhanhara Bangla — R8 rules for the release build.
# The keyboard service and activities are kept automatically (they are named in the manifest),
# and nothing is loaded by reflection, so the defaults are enough. Keep line numbers so crash
# reports from Play Console can be read.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
