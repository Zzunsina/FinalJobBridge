import { useResource } from './useResource';
export function useAdAvailability(code,jobId='',excludeOrderId='') {
 const key=code+':'+jobId+':'+excludeOrderId;
 const resource=useResource('/company/ad-availability?productCode='+encodeURIComponent(code)+'&jobId='+encodeURIComponent(jobId)+'&excludeOrderId='+encodeURIComponent(excludeOrderId),{refreshInterval:30000});
 return {...resource,data:resource.data?.requestKey===key?resource.data:null};
}
