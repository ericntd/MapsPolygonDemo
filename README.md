# MapsPolygonDemo

2 separate problems:
**Problem #1) Lag and ANR**
Steps to reproduce:
1a) Launch  the demo app
1b) Tap on **Draw At Once**
1c) Observe that app is frozen, you cannot interact with the app for 5-10 seconds, after that all the polygons are drawn covering Jakarta
1d) Leave the app for 10 - 15 minutes (more than 2 repetition of fetch data + draw 5142 polygons) and observe ANR

**Problem #2) Missing polygons**
Steps to reproduce:
2a) Launch  the demo app
2b) Tap on **Draw in Batches** (it draws 500 polygons, waits 1 second, draw another 500, waits 1 second and so on)
2c) Observe after 20 seconds or so, the drawing process finishes but there are a lot of polygons missing (Screen Shot 2018-07-26 at 7.03.11 PM.png )
(ANR eventually happens after 1 hour or so)
