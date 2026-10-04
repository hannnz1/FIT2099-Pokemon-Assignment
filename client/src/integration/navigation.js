export function nextMode(mode,view){
  if(!['world','battle','dialog'].includes(mode))return mode;
  if(view.dialog)return 'dialog';
  return view.encounterView?'battle':'world';
}
