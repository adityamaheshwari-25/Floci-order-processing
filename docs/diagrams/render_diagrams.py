"""Rebuild the documentation PNGs with Python and Pillow."""
from pathlib import Path
import math
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent
SCALE = 2
INK, MUTED, BLUE, GREEN = '#172B4D', '#526580', '#2563EB', '#087F70'

def font(size, bold=False):
    names = ['C:/Windows/Fonts/segoeui' + ('b' if bold else '') + '.ttf',
             '/usr/share/fonts/truetype/dejavu/DejaVuSans' + ('-Bold' if bold else '') + '.ttf']
    for name in names:
        if Path(name).exists():
            return ImageFont.truetype(name, size * SCALE)
    return ImageFont.load_default(size=size * SCALE)

def start(title, subtitle, height):
    global im, d
    im = Image.new('RGB', (1400*SCALE, height*SCALE), '#FFFFFF')
    d = ImageDraw.Draw(im)
    text((60, 40), title, 34, bold=True)
    text((60, 92), subtitle, 19, MUTED)

def text(pos, value, size=20, color=INK, bold=False, anchor=None):
    d.text(tuple(int(v*SCALE) for v in pos), value, font=font(size, bold), fill=color, anchor=anchor)

def box(rect, title, lines=(), fill='#F1F6FF', border='#B8CDF4', title_color=INK):
    x,y,r,b=rect
    d.rounded_rectangle(tuple(v*SCALE for v in rect), radius=16*SCALE, fill=fill, outline=border, width=2*SCALE)
    text(((x+r)/2,y+22),title,23,title_color,True,'mt')
    for i,line in enumerate(lines):
        text(((x+r)/2,y+62+i*29),line,19,MUTED,anchor='mt')

def arrow(points, color=BLUE):
    pts=[(int(x*SCALE),int(y*SCALE)) for x,y in points]
    d.line(pts,fill=color,width=3*SCALE,joint='curve')
    x,y=pts[-1]; px,py=pts[-2]; a=math.atan2(y-py,x-px)
    d.polygon([(x,y),(x-13*SCALE*math.cos(a-.45),y-13*SCALE*math.sin(a-.45)),
               (x-13*SCALE*math.cos(a+.45),y-13*SCALE*math.sin(a+.45))],fill=color)

def label(x,y,value,color=BLUE):
    f=font(18); bounds=d.textbbox((x*SCALE,y*SCALE),value,font=f)
    d.rectangle((bounds[0]-6*SCALE,bounds[1]-4*SCALE,bounds[2]+6*SCALE,bounds[3]+4*SCALE),fill='white')
    text((x,y),value,18,color)

start('Local network and container setup', 'Two addresses, one Floci service — the address depends on where the caller runs.', 1140)
box((40,145,1360,1080),'Your computer',fill='#FAFBFE',border='#CCD6E4')
box((95,220,580,355),'Terraform', ['Creates AWS resources in Floci', 'aws_endpoint_url = http://localhost:4566'])
box((820,220,1300,355),'Spring Boot Order API', ['Runs on the host in this demo', 'AWS API calls → http://localhost:4566'])
box((410,415,990,525),'Published host port', ['localhost:4566 → container port 4566'])
arrow([(335,355),(335,470),(410,470)])
arrow([(1060,355),(1060,470),(990,470)])
box((75,585,1325,1035),'Podman network: floci-net',fill='#EFF8F6',border='#8DC8BC',title_color=GREEN)
box((120,670,650,925),'Floci container', ['Network hostname: floci', 'HTTP endpoint: http://floci:4566', '', 'Emulates SQS, DynamoDB, S3,', 'Lambda APIs, IAM and STS'])
box((820,670,1280,925),'Lambda runtime container', ['Separate container created by Floci', 'Java 21 + order-processor.jar', '', 'AWS_ENDPOINT_URL is set from', 'lambda_endpoint_url'])
arrow([(500,525),(500,645),(385,645),(385,670)])
label(725,550,'Port mapping: 4566:4566')
arrow([(650,715),(820,715)],GREEN)
label(665,675,'Invoke')
arrow([(1050,925),(1050,982),(385,982),(385,925)],GREEN)
label(525,970,'Processor → http://floci:4566',GREEN)
text((100,1096),'localhost inside Lambda refers to Lambda itself. No published Lambda port is needed for its calls to Floci.',18,MUTED)
im.save(OUT/'local-container-network.png',dpi=(300,300))

start('Order processing from request to receipt', 'Logical service flow — storage and queues are emulated by Floci; Java processing runs in a separate container.', 1200)
box((480,155,920,245),'Client', ['Submits an order'])
box((480,310,920,425),'Order API', ['Validates request and calculates total'])
arrow([(700,245),(700,310)])
label(730,264,'1. POST /api/v1/orders')
box((50,500,440,660),'DynamoDB · orders', ['Initial status: RECEIVED', 'Processing state and receipt key'],fill='#EEF8F6',border='#8DC8BC')
box((530,500,900,660),'SQS · order-events', ['OrderPlacedEvent', 'At-least-once delivery'])
arrow([(480,365),(245,365),(245,500)])
label(65,395,'2. Save initial order')
arrow([(715,425),(715,500)])
label(750,450,'3. Publish event')
box((990,500,1350,660),'SQS · dead-letter queue', ['order-events-dlq', 'Repeated failures'],fill='#FFF6EC',border='#E6BB85')
arrow([(900,580),(990,580)],'#A96518')
label(931,688,'After retry limit', '#A96518')
box((480,785,940,945),'Lambda · order-processor', ['Separate Java 21 runtime container', '5. Claim order; skip completed duplicates'])
arrow([(715,660),(715,785)])
label(475,720,'4. Event mapping polls SQS and invokes Lambda')
arrow([(480,835),(350,835),(350,660)],GREEN)
label(115,745,'5. Read / claim order',GREEN)
arrow([(480,905),(90,905),(90,660)],GREEN)
label(105,935,'7. Mark COMPLETED; save receipt key',GREEN)
box((990,785,1350,945),'S3 · order-receipts', ['receipts/<orderId>.json', 'Deterministic key'],fill='#EEF8F6',border='#8DC8BC')
arrow([(940,850),(990,850)],GREEN)
label(995,745,'6. Write receipt',GREEN)
box((60,1020,1340,1140),'Duplicate delivery is expected', ['Completed orders are acknowledged without creating another receipt. Failed messages can be retried.'],fill='#FAFBFE',border='#CCD6E4')
im.save(OUT/'order-processing-flow.png',dpi=(300,300))
