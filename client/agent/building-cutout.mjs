// Remove only exterior grass from a display copy of the existing building atlas.
export function buildingCutout(pixels,width,height){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<=0||height<=0||!ArrayBuffer.isView(pixels)||pixels.BYTES_PER_ELEMENT!==1||pixels.length!==width*height*4)throw new Error('INVALID_BUILDING_PIXELS');
 const out=new Uint8ClampedArray(pixels),n=width*height,removed=new Uint8Array(n),queue=new Int32Array(n);let head=0,tail=0;
 const background=i=>{const p=i*4;return out[p+3]===0||(out[p+1]>out[p]+12&&out[p+1]>out[p+2]+18&&out[p]<225);};
 const enqueue=i=>{if(!removed[i]&&background(i)){removed[i]=1;queue[tail++]=i;}};
 for(let x=0;x<width;x++){enqueue(x);enqueue((height-1)*width+x);}for(let y=0;y<height;y++){enqueue(y*width);enqueue(y*width+width-1);}
 while(head<tail){const i=queue[head++],x=i%width,y=Math.floor(i/width);if(x)enqueue(i-1);if(x+1<width)enqueue(i+1);if(y)enqueue(i-width);if(y+1<height)enqueue(i+width);}
 for(let i=0;i<n;i++)if(removed[i])out[i*4+3]=0;
 // A map crop may also include detached flowers/signposts; keep the main building.
 const visited=new Uint8Array(n);let largest=[];
 for(let seed=0;seed<n;seed++){
  if(visited[seed]||out[seed*4+3]===0)continue;head=0;tail=1;queue[0]=seed;visited[seed]=1;const component=[];
  while(head<tail){const i=queue[head++],x=i%width,y=Math.floor(i/width);component.push(i);for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const nx=x+dx,ny=y+dy;if(nx<0||nx>=width||ny<0||ny>=height)continue;const j=ny*width+nx;if(!visited[j]&&out[j*4+3]){visited[j]=1;queue[tail++]=j;}}}
  if(component.length>largest.length)largest=component;
 }
 const keep=new Uint8Array(n);for(const i of largest)keep[i]=1;for(let i=0;i<n;i++)if(!keep[i])out[i*4+3]=0;
 return out;
}
export function installBuildingCutout(textures,source,art){
 const c=art.crop,key=art.frame+'-cutout',texture=textures.createCanvas(key,c.width,c.height),context=texture.context;
 context.imageSmoothingEnabled=false;context.drawImage(source,c.x,c.y,c.width,c.height,0,0,c.width,c.height);
 const image=context.getImageData(0,0,c.width,c.height);image.data.set(buildingCutout(image.data,c.width,c.height));context.putImageData(image,0,0);texture.refresh();return key;
}
