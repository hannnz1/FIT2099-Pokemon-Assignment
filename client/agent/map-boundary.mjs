// Read-only geometry from authoritative dimensions/collision; never changes navigation.
export function boundaryLayout({width,height,startX=0,startY=0,cols=width,rows=height,tile=64,x=0,y=0}){
 const rect={x,y,width:cols*tile,height:rows*tile};
 const edges=[{side:'north',kind:startY<=0?'world':'viewport',x1:x,y1:y,x2:x+rect.width,y2:y},{side:'east',kind:startX+cols>=width?'world':'viewport',x1:x+rect.width,y1:y,x2:x+rect.width,y2:y+rect.height},{side:'south',kind:startY+rows>=height?'world':'viewport',x1:x,y1:y+rect.height,x2:x+rect.width,y2:y+rect.height},{side:'west',kind:startX<=0?'world':'viewport',x1:x,y1:y,x2:x,y2:y+rect.height}];
 return {width,height,startX,startY,cols,rows,tile,rect,edges};
}
export function blockedCells(map){
 if(Array.isArray(map.collision))return map.collision.flatMap((value,i)=>Number(value)!==0?[{x:i%map.width,y:Math.floor(i/map.width)}]:[]);
 return (map.tiles??[]).filter(t=>t.kind==='wall').map(({x,y})=>({x,y}));
}
export function mapExits(map,names={}){return (map.exits??[]).filter(e=>e&&Number.isInteger(e.x)&&Number.isInteger(e.y)&&e.regionId).map(e=>({...e,label:e.label??'前往'+(names[e.regionId]??e.regionId)}));}
export function boundaryDescription(b,{showBlocked=true,exitLabel="金色门：区域出口"}={}){return `地图 ${b.width} × ${b.height} 格 · 当前视口 X ${Math.floor(b.startX)}–${Math.ceil(b.startX+b.cols)-1} / Y ${Math.floor(b.startY)}–${Math.ceil(b.startY+b.rows)-1} · 金色实线：世界边界；蓝色虚线：视口外仍有地图；${showBlocked?"斜线格：地形不可通行；":""}${exitLabel}。`;}
export function attachBoundaryLegend(host,options={}){
 const node=document.createElement('p');node.className='map-boundary-legend';node.setAttribute('aria-label','地图边界图例');node.style.cssText='box-sizing:border-box;max-width:100%;min-width:0;margin:8px 0;padding:8px 10px;border:1px solid #718c98;border-radius:6px;color:#dbe9ec;background:#203b32;font-size:12px;line-height:1.6;overflow-wrap:anywhere;white-space:normal;';host.after(node);
 return {update(b,exits=[]){node.textContent=boundaryDescription(b,options)+(exits.length?' 出口：'+exits.map(e=>`${e.label}（${e.x},${e.y}）`).join('；'):'');host.dataset.boundary=JSON.stringify({width:b.width,height:b.height,edges:b.edges.map(({side,kind})=>({side,kind}))});},fallback(message){node.textContent=message+' 可继续使用下方文字地图、区域与方向按钮。';},destroy(){node.remove();}};
}
export function drawBoundary(scene,b,{layer=null,depth=3900}={}){
 const g=scene.add.graphics().setDepth(depth);if(layer)layer.add(g);
 // Keep strokes inside the canvas so the focus outline cannot hide map edges.
 const left=b.rect.x+5,right=b.rect.x+b.rect.width-5,top=b.rect.y+5,bottom=b.rect.y+b.rect.height-5;
 const innerX=value=>Math.max(left,Math.min(right,value)),innerY=value=>Math.max(top,Math.min(bottom,value));
 for(const edge of b.edges){const x1=innerX(edge.x1),y1=innerY(edge.y1),x2=innerX(edge.x2),y2=innerY(edge.y2),dx=x2-x1,dy=y2-y1,length=Math.hypot(dx,dy);g.lineStyle(edge.kind==='world'?5:3,edge.kind==='world'?0xffd36a:0x79d9ef,1);
  if(edge.kind==='world')g.lineBetween(x1,y1,x2,y2);else for(let offset=0;offset<length;offset+=20){const end=Math.min(offset+11,length);g.lineBetween(x1+dx*offset/length,y1+dy*offset/length,x1+dx*end/length,y1+dy*end/length);}
 }
 return g;
}
export function drawBlocked(scene,cells,{tile=64,x=0,y=0,startX=0,startY=0,cols=Infinity,rows=Infinity,layer=null,depth=3500}={}){
 const g=scene.add.graphics().setDepth(depth);if(layer)layer.add(g);g.lineStyle(2,0xffb2a3,.75);g.fillStyle(0x412e30,.19);
 for(const cell of cells){if(cell.x<startX||cell.y<startY||cell.x>=startX+cols||cell.y>=startY+rows)continue;const px=x+(cell.x-startX)*tile,py=y+(cell.y-startY)*tile;g.fillRect(px+2,py+2,tile-4,tile-4);g.strokeRect(px+2,py+2,tile-4,tile-4);for(let i=12;i<tile;i+=16)g.lineBetween(px+3,py+i,px+i,py+3);}
 return g;
}
