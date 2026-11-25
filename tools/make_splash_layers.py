import numpy as np, cv2
from PIL import Image, ImageDraw
D='/root/.copilot/uploads/2026-11-25-b5b1bac6/'
C=np.array(Image.open(D+'upload-4380bdc7-60a6-4308-a14f-e1967ebc941e.png')).astype(np.float32)/255
S=1.7; CX,CY=279,261            # cube-frame centre -> canvas centre
OUT='/tmp/sp/out/'
import os; os.makedirs(OUT,exist_ok=True)
H,W=C.shape[:2]
def P(pts): return [(x/2+55,y/2+16) for x,y in pts]
polys={
 'chev':[[(258,430),(345,393),(425,438),(515,388),(600,432),(600,525),(430,592),(258,520)]],
 'topslab':[[(258,265),(370,200),(410,215),(570,130),(700,210),(700,250),(430,402),(260,322)]],
 'topB':[[(290,120),(430,35),(555,105),(555,150),(420,215),(290,150)]],
 'topA':[[(140,140),(275,132),(358,180),(358,245),(300,290),(250,310),(145,232)]],
 'cyan':[[(432,610),(470,590),(600,525),(604,740),(450,806),(352,750),(352,660)]],
 'D':[[(35,310),(140,252),(330,362),(325,400),(250,440),(250,582),(215,592),(90,508),(40,470)]],
 'E':[[(48,535),(130,498),(352,655),(352,742),(285,790),(255,832),(140,752),(55,692)]],
 'F':[[(535,355),(700,265),(750,252),(822,310),(822,470),(650,602),(610,592),(610,430),(535,380)]],
 'G':[[(608,528),(760,508),(808,530),(808,692),(640,812),(608,792)]],
 'I':[[(276,770),(310,775),(440,842),(590,738),(606,750),(606,832),(440,948),(278,852)]],
}
order=['chev','topslab','topB','topA','cyan','D','E','F','G','I']
group={'topslab':'top','topB':'top','topA':'top','D':'left','E':'left','F':'right','G':'right','cyan':'right','I':'bottom','chev':'core'}
lab=np.full((H,W),-1,np.int8)
for i,k in enumerate(order):
    m=Image.new('L',(W,H),0); d=ImageDraw.Draw(m)
    for p in polys[k]: d.polygon(P(p),fill=255)
    m=np.array(m)>0
    lab[(lab<0)&m]=i
gname=['core','top','left','right','bottom']
cube=np.zeros((H,W),bool); cube[26:496,65:483]=True
A=C[...,3]
layers={}
for g in gname:
    ids=[i for i,k in enumerate(order) if group[k]==g]
    m=np.isin(lab,ids).astype(np.float32)
    if g=='core': m=np.maximum(m,((lab<0)&cube).astype(np.float32))
    m=cv2.GaussianBlur(m,(0,0),1.1)
    if g!='core': m=np.where(np.isin(lab,ids),np.maximum(m,0.0),m)
    layers[g]=m
# dark interior so removed blocks leave a mechanism, not a hole
solid=(cv2.GaussianBlur(((A>0.6)&cube).astype(np.float32),(0,0),2)>0.5).astype(np.uint8)
hull=np.zeros((H,W),np.uint8)
cnts,_=cv2.findContours(solid,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
cv2.fillConvexPoly(hull,cv2.convexHull(np.vstack(cnts)),1)
hull=cv2.erode(hull,np.ones((3,3),np.uint8),iterations=26)
dark=np.zeros((H,W,4),np.float32)
yy,xx=np.mgrid[0:H,0:W]; r=np.hypot((xx-279)/200,(yy-261)/230)
dark[...,0]=(0.10+0.06*(1-np.clip(r,0,1)))*1; dark[...,1]=0.05+0.02*(1-np.clip(r,0,1)); dark[...,2]=0.26+0.10*(1-np.clip(r,0,1))
dark[...,3]=cv2.GaussianBlur(hull.astype(np.float32),(0,0),6)
def over(top,bot):  # premultiplied float RGBA
    return top+bot*(1-top[...,3:4])
def prem(rgba): return np.dstack([rgba[...,:3]*rgba[...,3:4],rgba[...,3:4]])
def to_canvas(pm,dx=0,dy=0,s=S):
    M=np.float32([[s,0,512-(CX-dx)*s],[0,s,512-(CY-dy)*s]])
    return cv2.warpAffine(pm,M,(1024,1024),flags=cv2.INTER_AREA if s<1 else cv2.INTER_CUBIC,borderValue=0)
def save(pm,name):
    a=np.clip(pm[...,3:4],0,1); rgb=np.where(a>1e-4,pm[...,:3]/np.maximum(a,1e-4),0)
    im=np.dstack([np.clip(rgb,0,1),np.clip(a,0,1)])
    Image.fromarray((im*255+.5).astype(np.uint8),'RGBA').save(OUT+name,optimize=True)
    return im
Cp=prem(C)
res={}
for g in gname:
    pm=Cp*layers[g][...,None]
    if g=='core': pm=over(pm,prem(dark)*1.0)
    res[g]=to_canvas(pm)
# orbits (collage arcs, same scale as the cube; offsets measured against the approved ring image)
from scipy import ndimage as ndi
_lab,_=ndi.label(ndi.binary_dilation(A>0.157,iterations=6))
def arc(ids,dx,dy):
    m=np.isin(_lab,ids).astype(np.float32); m=cv2.GaussianBlur(m,(0,0),2)
    out=to_canvas(Cp*m[...,None],dx=dx,dy=dy)
    Mz=np.float32([[0.94,0,512*(1-0.94)],[0,0.94,512*(1-0.94)]])
    return cv2.warpAffine(out,Mz,(1024,1024),flags=cv2.INTER_AREA)
res['orbit_front']=arc([3],-979,1)
res['orbit_back']=arc([2,4],-489,8)
import sys
names={'core':'splash_cube_core','top':'splash_cube_top','left':'splash_cube_left','right':'splash_cube_right','bottom':'splash_cube_bottom','orbit_back':'splash_orbit_back','orbit_front':'splash_orbit_front'}
for k,n in names.items(): save(res[k],n+'.png')
# control composite
bg=np.zeros((1024,1024,4),np.float32); bg[...,:3]=np.array([0.04,0.07,0.18])*1; bg[...,3]=1
comp=prem(bg)
for k in ['orbit_back','core','bottom','left','right','top','orbit_front']: comp=over(res[k],comp)
Image.fromarray((np.clip(comp[...,:3],0,1)*255).astype(np.uint8)).save('/tmp/sp/control2.png')
