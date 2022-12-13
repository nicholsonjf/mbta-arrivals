import re

f = open("original_data.txt")
r = f.read()
m = re.findall(r'(?<=event: update\ndata: ){.*}', r)
n = open("cleaned_data.txt", 'w')

for match in m:
    n.write(match)
    n.write("\n")