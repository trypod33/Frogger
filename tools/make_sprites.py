from PIL import Image, ImageDraw, ImageFilter
import math, os

S = 8  # supersample scale
OUT = "/workspace/frogger/app/src/main/res/drawable-nodpi"
os.makedirs(OUT, exist_ok=True)

def canvas(w, h):
    img = Image.new("RGBA", (w*S, h*S), (0,0,0,0))
    return img, ImageDraw.Draw(img)

def save(img, name, w, h):
    img = img.resize((w, h), Image.LANCZOS)
    img.save(os.path.join(OUT, name))
    return img

def rrect(d, box, r, fill, outline=None, width=0):
    d.rounded_rectangle([box[0]*S, box[1]*S, box[2]*S, box[3]*S], radius=r*S, fill=fill, outline=outline, width=width*S if width else 0)

def ellipse(d, box, fill):
    d.ellipse([box[0]*S, box[1]*S, box[2]*S, box[3]*S], fill=fill)

# ---------- palette (toy / mockup-3 style) ----------
BODY_RED=(214,54,42); BODY_RED_D=(160,32,26)
BODY_BLUE=(66,118,232); BODY_BLUE_D=(40,80,168)
BODY_YELLOW=(244,182,44); BODY_YELLOW_D=(180,128,24)
BODY_GREEN=(96,196,72); BODY_GREEN_D=(60,140,48)
GLASS=(168,216,244); GLASS_D=(108,168,208)
TIRE=(38,38,46); TIRE_D=(20,20,26)
CHROME=(224,224,228)
WOOD=(178,116,56); WOOD_L=(214,158,92); WOOD_D=(128,78,34)
SHELL=(86,190,86); SHELL_L=(150,224,120); SHELL_D=(48,134,58)

def car(w=13,h=8,body=BODY_RED,dark=BODY_RED_D, taxi_sign=False):
    """top-down toy car facing LEFT: windshield on the left side"""
    img,d = canvas(w,h)
    # wheels
    for wy in (0.6, h-2.0):
        for wx in (1.4, w-3.4):
            rrect(d,(wx,wy,wx+2.4,wy+2.4),0.9,TIRE)
    # body
    rrect(d,(0.4,0.9,w-0.4,h-0.9),2.6,body)
    # shading: lighter top band, darker bottom
    rrect(d,(1.2,1.5,w-1.2,3.2),1.6,tuple(min(255,c+28) for c in body))
    rrect(d,(1.2,h-2.8,w-1.2,h-1.4),1.6,dark)
    # windshield (left) + rear window (right)
    rrect(d,(1.0,1.9,3.2,h-1.9),0.9,GLASS)
    rrect(d,(w-3.4,2.1,w-1.0,h-2.1),0.8,GLASS_D)
    # roof
    rrect(d,(3.6,2.0,w-3.8,h-2.0),1.4,tuple(min(255,c+14) for c in body))
    if taxi_sign:
        ellipse(d,(w/2-1.1,2.4,w/2+1.1,4.6),BODY_YELLOW_D)
        ellipse(d,(w/2-0.6,2.9,w/2+0.6,4.1),(255,244,200))
    # headlights (left edge, facing left)
    ellipse(d,(0.4,2.0,1.3,3.2),(255,240,170))
    ellipse(d,(0.4,h-3.2,1.3,h-2.0),(255,240,170))
    return img

def racer():
    # low sleek sports car, red with white stripe
    w,h=13,8
    img,d=canvas(w,h)
    for wy in (0.5,h-1.9):
        for wx in (1.2,w-3.2):
            rrect(d,(wx,wy,wx+2.6,wy+2.4),1.0,TIRE)
    rrect(d,(0.2,1.0,w-0.2,h-1.0),2.8,BODY_RED)
    rrect(d,(1.0,1.6,w-1.0,2.8),1.2,(240,96,84))
    rrect(d,(1.0,h-2.6,w-1.0,h-1.3),1.2,BODY_RED_D)
    # cockpit
    rrect(d,(3.2,2.0,w-4.0,h-2.0),1.6,(40,26,32))
    # white stripe
    rrect(d,(w/2-0.8,1.4,w/2+0.8,h-1.4),0.6,(240,240,244))
    ellipse(d,(0.4,2.0,1.2,3.0),(255,238,160))
    ellipse(d,(0.4,h-3.0,1.2,h-2.0),(255,238,160))
    return img

def dozer():
    w,h=13,8
    img,d=canvas(w,h)
    # tracks top+bottom
    rrect(d,(1.6,0.2,w-1.4,2.2),0.9,TIRE)
    rrect(d,(1.6,h-2.2,w-1.4,h-0.2),0.9,TIRE)
    for x in (2.6,4.6,6.6,8.6,10.6):
        d.rectangle([x*S,0.6*S,(x+1.0)*S,(h-0.6)*S],fill=(70,70,80))
    # blade on the left
    rrect(d,(0.2,0.8,1.6,h-0.8),0.7,BODY_YELLOW)
    # cab body
    rrect(d,(2.4,1.6,w-0.6,h-1.6),1.6,BODY_YELLOW)
    rrect(d,(2.9,2.1,w-1.1,3.3),1.0,(252,214,96))
    rrect(d,(2.9,h-2.9,w-1.1,h-1.9),1.0,BODY_YELLOW_D)
    # cabin window
    rrect(d,(w-4.6,2.4,w-1.8,h-2.4),0.8,GLASS)
    ellipse(d,(w-2.6,3.2,w-2.0,3.8),(255,120,40))  # beacon
    return img

def beetle():
    w,h=13,8
    img,d=canvas(w,h)
    for wy in (0.6,h-2.0):
        for wx in (1.3,w-3.3):
            rrect(d,(wx,wy,wx+2.4,wy+2.4),0.9,TIRE)
    rrect(d,(0.4,1.0,w-0.4,h-1.0),2.8,BODY_YELLOW)
    rrect(d,(1.2,1.6,w-1.2,3.0),1.4,(252,214,96))
    rrect(d,(1.2,h-2.8,w-1.2,h-1.4),1.4,BODY_YELLOW_D)
    rrect(d,(1.0,2.0,3.0,h-2.0),0.9,GLASS)
    rrect(d,(w-3.2,2.2,w-1.0,h-2.2),0.8,GLASS_D)
    # bug spots
    ellipse(d,(5.4,2.6,7.0,4.2),BODY_RED)
    ellipse(d,(w-6.6,h-4.2,w-5.0,h-2.6),BODY_RED)
    ellipse(d,(0.4,2.0,1.2,3.0),(255,240,170))
    ellipse(d,(0.4,h-3.0,1.2,h-2.0),(255,240,170))
    return img

def truck():
    """32x11 toy semi facing RIGHT: trailer left, cab right"""
    w,h=32,11
    img,d=canvas(w,h)
    # wheels
    for wx in (3.0,8.0,13.0,24.0):
        for wy in (h-2.6,):
            rrect(d,(wx,wy,wx+2.6,wy+2.4),1.0,TIRE)
    # trailer
    rrect(d,(0.6,0.8,22.5,h-1.6),1.6,(238,238,242))
    rrect(d,(1.4,1.5,21.7,h-2.4),1.2,(250,250,252))
    rrect(d,(1.4,h-3.4,21.7,h-2.0),1.0,(208,208,214))
    # ribs
    for x in (4.5,8.5,12.5,16.5):
        d.rectangle([x*S,1.8*S,(x+0.7)*S,(h-2.6)*S],fill=(216,216,222))
    # gap
    rrect(d,(22.5,1.4,24.0,h-1.8),0.8,TIRE_D)
    # cab
    rrect(d,(23.6,1.0,w-0.5,h-0.9),1.6,BODY_RED)
    rrect(d,(24.2,1.6,w-1.2,3.0),1.0,(240,96,84))
    rrect(d,(24.2,h-2.8,w-1.2,h-1.3),1.0,BODY_RED_D)
    # windshield right
    rrect(d,(w-3.2,1.8,w-1.0,h-2.2),0.8,GLASS)
    # exhaust stack
    d.rectangle([24.0*S,0.2*S,24.6*S,1.4*S],fill=(120,120,128))
    return img

def log(lenPx):
    w,h=lenPx,10
    img,d=canvas(w,h)
    # body cylinder
    rrect(d,(0.2,0.6,w-0.2,h-0.6),4.4,WOOD)
    # top light band
    rrect(d,(2.4,1.4,w-2.4,4.2),2.4,WOOD_L)
    # bottom shade
    rrect(d,(2.4,h-4.0,w-2.4,h-1.2),2.4,WOOD_D)
    # bark rings
    for x in (12.0,lenPx/2,lenPx-12.0):
        rrect(d,(x,1.8,x+3.4,h-1.8),1.2,WOOD_D)
        rrect(d,(x+0.8,2.6,x+2.6,h-2.6),0.8,WOOD_L)
    # end caps
    ellipse(d,(0.2,1.2,5.2,h-1.2),(206,148,88))
    ellipse(d,(1.0,2.0,4.4,h-2.0),(236,196,140))
    ellipse(d,(1.7,2.8,3.7,h-2.8),(190,138,82))
    ellipse(d,(w-5.2,1.2,w-0.2,h-1.2),(196,138,80))
    ellipse(d,(w-4.4,2.0,w-1.0,h-2.0),(232,190,134))
    ellipse(d,(w-3.7,2.8,w-1.7,h-2.8),(184,132,78))
    # ripple shadow beneath
    return img

def turtle():
    """12x8 toy turtle facing LEFT"""
    w,h=12,8
    img,d=canvas(w,h)
    # flippers
    rrect(d,(3.2,0.1,5.4,1.6),0.6,SHELL_D)
    rrect(d,(3.2,h-1.6,5.4,h-0.1),0.6,SHELL_D)
    rrect(d,(w-4.4,0.6,w-2.2,2.0),0.6,SHELL_D)
    rrect(d,(w-4.4,h-2.0,w-2.2,h-0.6),0.6,SHELL_D)
    # shell
    ellipse(d,(1.4,0.6,w-1.0,h-0.6),SHELL)
    # shell pattern
    ellipse(d,(3.2,1.6,w-3.0,h-1.6),SHELL_L)
    ellipse(d,(4.6,2.4,w-4.4,h-2.4),SHELL)
    ellipse(d,(5.4,3.0,7.6,5.0),SHELL_D)
    # rim
    ellipse(d,(1.4,0.6,w-1.0,h-0.6),None)  # noop for order
    # head left
    ellipse(d,(0.0,2.4,2.6,5.6),SHELL_L)
    ellipse(d,(0.4,3.0,1.8,4.4),(34,84,40))
    ellipse(d,(0.5,3.3,1.0,3.8),(240,250,240))
    return img

def frog(hop=False):
    """14x14 toy frog facing UP"""
    w,h=14,14
    img,d=canvas(w,h)
    BODY=(104,208,92); L=(176,238,150); D=(56,140,58)
    if hop:
        # legs stretched out sideways/back
        rrect(d,(0.2,7.4,3.4,10.6),1.4,D)
        rrect(d,(w-3.4,7.4,w-0.2,10.6),1.4,D)
        rrect(d,(3.0,10.4,5.4,13.0),1.2,D)
        rrect(d,(w-5.4,10.4,w-3.0,13.0),1.2,D)
    else:
        # tucked legs
        rrect(d,(1.0,8.4,4.0,12.6),1.6,D)
        rrect(d,(w-4.0,8.4,w-1.0,12.6),1.6,D)
        ellipse(d,(1.6,10.6,4.6,13.4),D)
        ellipse(d,(w-4.6,10.6,w-1.6,13.4),D)
    # body
    ellipse(d,(2.6,3.0,w-2.6,h-2.2),BODY)
    ellipse(d,(3.8,4.2,w-3.8,h-4.0),L)
    # belly hint
    ellipse(d,(5.0,7.4,w-5.0,h-3.2),(222,246,206))
    # eyes
    ellipse(d,(2.6,0.6,5.8,3.8),BODY)
    ellipse(d,(w-5.8,0.6,w-2.6,3.8),BODY)
    ellipse(d,(3.2,1.2,5.0,3.0),(250,252,250))
    ellipse(d,(w-5.0,1.2,w-3.2,3.0),(250,252,250))
    ellipse(d,(3.8,1.8,4.6,2.6),(24,28,30))
    ellipse(d,(w-4.6,1.8,w-3.8,2.6),(24,28,30))
    return img

def fly():
    w,h=8,6
    img,d=canvas(w,h)
    ellipse(d,(2.0,1.8,6.4,5.0),BODY_RED)
    ellipse(d,(3.0,2.6,5.4,4.4),(255,150,120))
    ellipse(d,(0.4,2.2,3.0,3.6),(210,232,250))   # wing
    ellipse(d,(0.4,3.0,3.0,4.4),(190,216,246))
    ellipse(d,(4.6,0.8,6.6,2.6),(150,60,200))    # head
    return img

def skull():
    w,h=14,14
    img,d=canvas(w,h)
    B=(232,232,236); D=(64,64,72)
    ellipse(d,(2.0,1.0,w-2.0,10.0),B)
    rrect(d,(4.4,9.0,w-4.4,12.6),1.2,B)
    ellipse(d,(4.2,3.6,7.4,6.8),D)
    ellipse(d,(w-7.4,3.6,w-4.2,6.8),D)
    d.polygon([(6.0*S,8.4*S),(7.0*S,8.4*S),(6.5*S,9.8*S)],fill=D)
    d.polygon([(7.6*S,8.4*S),(8.6*S,8.4*S),(8.1*S,9.8*S)],fill=D)
    return img

save(car(body=BODY_BLUE,dark=BODY_BLUE_D),"spr_car.png",13,8)
save(racer(),"spr_racer.png",13,8)
save(dozer(),"spr_dozer.png",13,8)
save(beetle(),"spr_beetle.png",13,8)
save(truck(),"spr_truck.png",32,11)
save(log(48),"spr_log3.png",48,10)
save(log(80),"spr_log5.png",80,10)
save(turtle(),"spr_turtle.png",12,8)
save(frog(False),"spr_frog_sit.png",14,14)
save(frog(True),"spr_frog_hop.png",14,14)
save(fly(),"spr_fly.png",8,6)
save(skull(),"spr_skull.png",14,14)
print("saved", len(os.listdir(OUT)))
