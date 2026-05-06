function convertFiles2(dirname, fileName)

    reloading = false;

    if ~reloading


    	files = dir(dirname);
    	results = struct();
    	results.tsDays = nan(numel(files),1);
    	i = 1;
    	mutantDetails = load(dirname + "/" + fileName + "_D0.mat").results.diversityTS;
    	mutants = mutantDetails.mutants;
    	daysM = mutantDetails.daysM;


	settings = load(dirname + "/" + fileName + "_D0.mat").settings;
	if cell2mat(settings{"MUTATION",1}) == 0
		mutants = [];
	end

    	for f = 1:numel(files)
        	fName = files(f).name

        
        	if startsWith(fName,fileName) && contains(fName, "_D")


            	loaded = load(dirname + "/" + fName);
            	if ~isfield(loaded.results.tsData{1}, "linIDs")
                	continue
            	end

            	day = loaded.results.tsDays(1);

		disp("running day: " + day)


   
            	if  isfield(loaded.results.tsData{1}, "settings")
                	loaded.results.tsData{1} = rmfield(loaded.results.tsData{1}, 'settings');
            	end

            	if  isfield(loaded.results, "metadata")
                	loaded.results = rmfield(loaded.results, 'metadata');
            	end

            	metadata = loaded.settings;
            	loaded = rmfield(loaded, 'settings');


            	results.tsDays(numel(results.tsDays) + 1) = day;
		i2 = numel(results.tsDays)
            	results.tsData{i2} = loaded.results.tsData{1};

            	[a, b] = getBirthHour(results.tsData{i2}.linIDs, mutants, daysM, day);
            
            	results.tsData{i2}.birthHour = a;
            	results.tsData{i2}.origins = b;
            	results.tsData{i2}.ageHour = (day * 24) - results.tsData{i2}.birthHour;

	        
            
            	simName = replace(loaded.name,"_D" + num2str(day), "");
            	results.lat = loaded.results.lat;
            	results.lon = loaded.results.lon;



            	%if isfield(loaded.results, "diversityTS") && numel(loaded.results.diversityTS.globals) > 0
                %	results.diversityTS = loaded.results.diversityTS;
            	%end
            	    %delete(dirname + "/" + fName);
        	    i = i + 1;
	
        	end
    	end

    	nonNaN = find(~isnan(results.tsDays) );
    	results.tsDays = results.tsDays(nonNaN);
    	results.tsData = results.tsData(nonNaN);
    	[~, sortRes] = sort(results.tsDays);
    	results.tsDays = results.tsDays(sortRes);
    	results.tsData = results.tsData(sortRes);
    	name = simName;
    else
	results = load(dirname + "/W=12dispK100000M0p001.mat").results;
	name = "W=12_K=100000_MUT=0.001_INIT=1";
    end



    results.metadata = load("defEnv.mat").defaultSettings;
    results.tenv = load("tenvMonthly.mat").tenv;

    %
    

    %%%%%%%%%%%%%%%%%%%%%%%%%%% display to verify correctness %%%%%%%%%%%%% 

    disp("VERIFIICATION:");


    disp("confirming ages")
    for i = 1:numel(results.tsDays);
        disp("day: " + num2str(results.tsDays(i)) + " max:" + num2str(max(results.tsData{i}.ageHour / 24)) + ...
            " min:" + num2str(min(results.tsData{i}.ageHour / 24))   + " mean: " + num2str(mean(results.tsData{i}.ageHour / 24)))
    end

    disp("Settings: ") ;
    disp(results.metadata);
    disp(size(results.tsDays) + " days");

    disp("Size day " + results.tsDays(1) + ": ");
    disp(size(results.tsData{1}.V));
    disp("Size day " + results.tsDays(end) + ": ");
    disp(size(results.tsData{end}.V));
    disp("Origins day " + results.tsDays(1) + ": ");
    disp(size(results.tsData{1}.origins));
    disp("Origins day " + results.tsDays(end) + ": ");
    disp(size(results.tsData{end}.origins));
    disp("Max day " + results.tsDays(1) + ": " + num2str(max(max(results.tsData{1}.V))));
    disp("Max day " + results.tsDays(end) + ": " + num2str(max(max(results.tsData{end}.V))));

    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    

    save(name, "results", '-v7.3');
    save(name, "name", '-append');



end

function [birthHours, origins] = getBirthHour(linIDs, mutantIDs, days, curDay)
    limit = floor(2147483647/6386);
    totalLim = limit * 6386;

    %if < Max.INT %2147483650

    origins = floor(linIDs ./ limit);
    
    origins = rem(origins, 6386) + 1;
    report.origins = origins;

    birthHours = zeros(1,numel(linIDs));


    disp(numel(mutantIDs))

    if numel(mutantIDs) > 0

    	mutantIDs = [mutantIDs; Inf(1, 6386)];

    	days = [days; curDay];

    	for loc = 1:numel(linIDs)
        	if(rem(loc,100000) == 0)
            		"processing lineage " + num2str(loc) + " out of " + num2str(numel(linIDs))
        	end
        	linID = linIDs(loc);
        	locCol = mutantIDs(: , origins(loc));
        	befores = find(locCol < linID);
        	birthHours(loc) = days(max(numel(befores),1)) * 24; 
    	end
    end

end