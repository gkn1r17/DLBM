function[] = ContinuousModel(folder, TMfile, neutral, seasonal, dispHours, growthHours, useGPU, w)

        format longG

        if exist(folder) == 0
	        mkdir(folder)
        end


        %% Parameters
        
        w
        k=1;
        tmax = 365 * 1e5;
        maxGr = 0.8;
        mort = 0.1;
        equilibrium = 0.875;
        locD = 4;

        saveDays = [0 36 46 58 73 92 115 145 183 230 290 365 460 578 728 917 1154 1453 1829 2303 2899 3650 4595 5785 7283 9168 11542 14531 18293 23030 28993 36500 45951 57849 72827 91684 115423 145309 182933 230299 289930 365000  459508  578486  728271  916839 1154231 1453091 1829333 2302994 2899298 3650000];
        saveDayCounter = 1
        

        neutral
        
        
        %% Transport Matrix
        
        load(TMfile);
        TM=ocean.B;

        n = 6386;
        vols = readmatrix("vols6386.csv"); %volume adjusted K, column vector
        
        % just in case
        if neutral
	        seasonal = 0
        end

        % selection
        if ~neutral

	        if seasonal
	                temps = readmatrix("temps6386-monthly.csv");
	                minTemps = min(temps')';
	                maxTemps = max(temps')';
	                rangeTemps = maxTemps - minTemps;
	                topts = [minTemps, minTemps + (rangeTemps * 0.25) , minTemps + (rangeTemps * 0.75) , maxTemps];
	                topts = reshape(topts', 1, n * locD);
                            grT = ones(n,n*locD,12);
	                for d = 1:12
	                     grT(:,:,d) = exp(-(((topts-temps(:,d) )./w).^2));
                            end
	        else
	                temps = readmatrix("temps6386.csv");
	                grT = exp(-(((temps-temps')./w).^2));
	                locD = 1;
	        end 
        else
	        locD = 1;
        end
        
        V = kron(eye(n),ones(1,locD));
        V = V .* vols .* equilibrium ./ locD;
        
        startT = 0;
        if exist(folder + "/sofar.mat") > 0
	% load previous run, and will also update startT
        	load(folder + "/sofar.mat");
	startT = startT + 1485550;
        end        

        while saveDays(saveDayCounter) < startT
        	saveDayCounter = saveDayCounter + 1
        end
        
        %% RUN THE SIMULATION
        month = 1



        % get intervals
        dispDay = 24/dispHours 
        if rem(dispDay,1) ~= 0;
	        error('dispHours must be a factor of 24');
        end
        growthDisp = dispHours/growthHours
        if rem(growthDisp,1) ~= 0;
	        error('dispHours must be divisible by growthHours');
        end



    %convert to GPU arrays for performance
	if useGPU
		if ~neutral
 			grT = gpuArray(grT);
		end
		V = gpuArray(V);
 		TM = gpuArray(TM);
	end

    lastTime = datetime("now");

    sofarInd = 1;
    for t=startT:tmax
        
                    %print so can assess progress
                    if rem(t,10) == 0
                            newTime = datetime("now");
                            disp("day " + num2str(t) + ", 10 days in " + string(newTime - lastTime));
                            lastTime = newTime;
                    end
                        
                    year = t / 365;
	                dayOfYear = rem(t, (floor(year) * 365) );
	                month = max( 1 ,  ceil(dayOfYear / (365 / 12)  )  );
                
	                if ~seasonal
	                   month = 1;
	                end
                
                
	                if t == saveDays(saveDayCounter);
	                    save(folder + "/D" + num2str(t) + ".mat", "V", '-v7.3')
	                    saveDayCounter = saveDayCounter + 1;
	                end	
	                
                
	                if rem(year,1) == 0
	                            save(folder + "/sofar" + num2str(sofarInd) + ".mat", "V", "-v7.3");
                                startT = t;
                                save(folder + "/sofar" + num2str(sofarInd) + ".mat", "startT", "-append");
                                sofarInd = ~sofarInd; %save two different sofar files (alternate sofar0 and sofar1) 
                                                            %for backup incase execution halts while one is generating
	                end
                
                
	                
	                %%%%%%%%%%%%%%%% MODEL LOOP %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%	
                
	                for dispI = 1:dispDay
                
		                %% growth   ( sum(V,2) = total abundance of each location )
		                for grI = 1:growthDisp
                
			                gr = max(0, (1 - (sum(V,2) ./ vols) ) * maxGr);    % growth rate for each location (column vector)
            		        if neutral
               			        V = V + (V .* gr) - (V .* mort);
            		        else
               			        V = V + ((V .* gr) .* grT(:,:,month) ) - (V .* mort);
            		        end
		                end
                
            	                %% dispersal
            	        V = TM*V;
                
	                end

        
    end
end
