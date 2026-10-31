from PIL import Image, ImageDraw
import random

W,H,TOP,CELL = 224, 272, 32, 16
ROWS=14
R_HOMES,R_WATER0,R_WATER1,R_MEDIAN,R_ROAD0,R_ROAD1,R_START=1,2,6,7,8,12,13
SC=4
img = Image.new("RGBA",(W*SC,(H)*SC),(0,0,0,255))
d = ImageDraw.Draw(img)

def load(n):
    im = Image.open(f"/workspace/frogger/app/src/main/res/drawable-nodpi/{n}").convert("RGBA")
    return im.resize((im.width*SC,im.height*SC),Image.NEAREST)

hedge = Image.new("RGBA",(224*SC,16*SC),(0,160,0,255))
hd = ImageDraw.Draw(hedge)
random.seed(7)
for i in range(120):
    x=random.randint(0,222*SC); y=random.randint(0,15*SC)
    hd.rectangle([x,y,x+3,y+3],fill=(0,104,0))
purple = Image.new("RGBA",(224*SC,16*SC),(152,72,200,255))
pd = ImageDraw.Draw(purple)
for i in range(120):
    x=random.randint(0,222*SC); y=random.randint(0,15*SC)
    pd.rectangle([x,y,x+3,y+3],fill=(112,40,154))

def paste(base,x,y): img.alpha_composite(base,(int(x*SC),int(y*SC)))
img.paste(hedge,(0,TOP*SC)); img.paste(hedge,(0,(TOP+R_HOMES*CELL)*SC))
d.rectangle([0,(TOP+R_WATER0*CELL)*SC,W*SC,(TOP+(R_WATER1+1)*CELL)*SC],fill=(0x20,0x20,0x8C))
img.paste(purple,(0,(TOP+R_MEDIAN*CELL)*SC))
img.paste(purple,(0,(TOP+R_START*CELL)*SC))
# dark road
d.rectangle([0,(TOP+R_ROAD0*CELL)*SC,W*SC,(TOP+(R_ROAD1+1)*CELL)*SC],fill=(16,16,16))

# homes bays
for b,col in enumerate([1,4,7,10,13]):
    bx,by=(col*CELL+2)*SC,(TOP+R_HOMES*CELL+2)*SC
    d.rectangle([bx,by,bx+12*SC,by+12*SC],fill=(8,8,88))

# lanes: sample items like the game
lanes=[(2,1,3,48,'log'),(3,-1,4,38,'turtle'),(4,1,2,80,'log'),(5,-1,3,38,'turtle'),(6,-1,4,38,'turtle'),
       (8,-1,2,32,'truck'),(9,1,3,12,'racer'),(10,-1,4,12,'car'),(11,1,3,12,'dozer'),(12,-1,4,12,'beetle')]
log3=load("spr_log3.png"); log5=load("spr_log5.png")
turtle=load("spr_turtle.png"); truck=load("spr_truck.png")
racer=load("spr_racer.png"); car=load("spr_car.png"); dozer=load("spr_dozer.png"); beetle=load("spr_beetle.png")
frog=load("spr_frog_sit.png"); flyb=load("spr_fly.png"); skull=load("spr_skull.png")

def mirror(im): return im.transpose(Image.FLIP_LEFT_RIGHT)

random.seed(3)
for row,dir,count,lenPx,typ in lanes:
    y=TOP+row*CELL
    period=W+lenPx; spacing=period/count
    for i in range(count):
        x=(random.random()*period)%period-lenPx
        if typ=='log':
            base=log5 if lenPx>60 else log3
            paste(base,x,y+3)
        elif typ=='turtle':
            for j in range(3):
                paste(turtle,x+j*13,y+4)
        elif typ=='truck':
            paste(truck,x,y+2)
        else:
            base={'racer':racer,'car':car,'dozer':dozer,'beetle':beetle}[typ]
            paste(base,x,y+5)

# frog mid-board + one in a bay + fly
paste(frog,104+6-7,TOP+11*CELL+8-7)
paste(frog,15*CELL+2,TOP+R_HOMES*CELL+2)
paste(flyb,4*CELL+4,TOP+R_HOMES*CELL+5)

img=img.convert("RGB").resize((W*SC,H*SC),Image.NEAREST)
img.save("/workspace/frogger/tools/preview_skin.png")
print("ok")
