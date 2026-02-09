function convertFiles(dirname, outName, experiment, timeStepsFile)
  %dirname = directory of csv files for converting into Matlab object
  %outName = where to save Matlab object
  %experiment = first part of filename of csv files e.g. "testSelectiveB_T09-02-2026 01-37-56"
  %timeStepsFile = path to csv timestamp file CSV's were saved to

    dirname
    experiment
    

    [settings, name] = loadSettings(dirname, experiment);

    
    days = readmatrix(timeStepsFile);

    tsData = cell(1, numel(days));

    %%%%%%%%%%%% LOAD CSV %%%%%%%%%%%%%%%%
    [diversityTS , limits] = loadTSLog(dirname, experiment, cell2mat(settings{"NUM_BOXES",1}));
    limits = [limits(2:end) Inf];

    %parpool; % Start a parallel pool if not already open

    %par
    for day = 1:numel(days)
        if cell2mat(settings{"DET_TRANSPORT",1})
            if isfile(dirname +  "/" + experiment +  days(day) + ".mat")
                tsData{day}.V = gather(load(dirname +  "/" + experiment +  days(day) + ".mat").V);
                tsData{day}.origins = 1:6386;
            else
                disp("WARNING: no data found for day " + num2str(day))
                tsData{day}.V = [];
                tsData{day}.origins = [];
            end
        else
            tsData{day} = convertFile(dirname, experiment, days(day), settings, limits);
        end
    end

    results = struct();
    results.tsData = tsData;
    results.tsDays = days;
    results.metadata = settings;
    results.diversityTS = diversityTS;
    
    latLon = readmatrix("latLon6386.csv", 'NumHeaderLines', 1);
    results.lat = latLon(:,1); 
    results.lon = latLon(:,2); 

    %display to verify correctness 
    disp("VERIFIICATION:");
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

    %
    
    name = outName + name;

    save(name + ".mat", "results", '-v7.3');
    save(name + ".mat", "name", '-append');
    save(name + ".mat", "settings", '-append');

end

%%
function [settings, name] = loadSettings(dirname, experiment)
    

    %load run specific settings file
    settingsFile = dirname + "/" + experiment + "_Settings.ini";
    opts = detectImportOptions(settingsFile, 'FileType', 'delimitedtext');
    opts.VariableTypes{2} = 'char';
    settings = readtable(settingsFile, opts);

    %remove blank rows
    settings = settings(settings{:,2} ~= "",:);

    name = "";

    settings = array2table(settings{:,2},'RowNames',settings{:,1});

    %convert booleans, where values unset (e.g. TEMP_FILE in neutral run)
    %counts as false
    falses = find(settings.Var1 == "false" | settings.Var1 == "none");
    trues = find(settings.Var1 == "true");
    settings{falses, 1} = {0};
    settings{trues, 1} = {1};

    %convert specified settings to numeric
    toConvert = {"INIT_LIN_SIZE", "NUM_BOXES", "MORTALITY_DAY",...
        "GROWTH_RATE_DAY", "GROWTH_HOURS", "DISP_HOURS", "DISP_SCALER",...
         "SIZE_REFUGE", "K", "TEMP_START_RANGE", "W", "MUTATION", "NUM_BOXES"};
    for tC = 1:numel(toConvert)
        if ischar(cell2mat(settings{toConvert{tC},1}))
            if cell2mat(settings{toConvert{tC},1}) == "Inf"
                settings{toConvert{tC},1} = {inf};
            else
                settings{toConvert{tC},1} = {str2double(cell2mat(settings{toConvert{tC},1}))};
            end
        end
    end



    %For convenience as used in a lot of down stream operations,
                    % create P_0 = starting mean population size
    if cell2mat(settings{"TOP_DOWN",1}) == 1
        settings{"P_0",1} = settings{"K",1};
    else
        settings{"P_0",1} = {cell2mat(settings{"K",1}) * (1 - (cell2mat(settings{"GROWTH_RATE_DAY",1})  / cell2mat(settings{"MORTALITY_DAY",1}))) };
    end

end

%%
%If value in settings file different to default then add new to simulation name.
%Make certain checks first to avoid overly long names / redundancy.
function name = addToName(val, defVal, settingName, nameSoFar)


    name = ""; %if no change to default that needs reporting return empty string i.e. do not change name

    if isnumeric(val{1}) && isnan(val{1})
        return;
    end

    if settingName == "P_0"
        return;
    end

    val = replace(string(val),".0","");
    defVal = replace(string(defVal),".0","");



    if settingName == "TEMP_FILE" || settingName == "TM_FILE" || settingName == "VOL_FILE" %compare just file name not path
            fileBits = strsplit(val, "/");
            val = fileBits(end);

            fileBits = strsplit(defVal, "/");
            defVal = fileBits(end);
    end

    if settingName == "TEMP_FILE"
        if val == "none" || val == "0"
            return;
        elseif val == "temps6386.csv"
            return;
        elseif val == "temps6386-daily.csv"
            val = "daily"; 
        end
    end
    

    if settingName == "TM_FILE"
        if val == "TMD6386bsFilt.txt"
            return;
        elseif val == "TMS6386bsFilt.txt"
            val = "surface";
        end
    end

    if settingName ~= "W" && settingName ~= "K"
        if val == defVal
            return;
        end
    end

    if nameSoFar ~= ""
        name = "_";
    end
    name = name + settingName + "=" + val;
end    

%%
function results = convertFile(dirname, experiment, day, settings, limits)

    all_vals = {};
    all_idNames = {};
    all_cellNums = {};

    num_cells = cell2mat(settings{"NUM_BOXES",1});
    popSize = cell2mat(settings{"K",1} );

    %%%%%%%%%%%% LOAD FROM CSV FILES %%%%%%%%%%%%%%%%
    %for every output file (parallel and distributed runs will contain
    %multiple output files for every distributed node - currently 7 is
    %maximum)
    dayFound = 0;
    for fnum = 1:7


        %find file
	    fPattern = experiment + "s[0-9]+_D" + day + "hr[0-9]+_N" + (fnum - 1) + ".*";
        files = dir(dirname); 
        files = string({files.name});
        found = 0;
        for fnI = 1:numel(files)
		    fn = files(fnI);
		    fMatches = regexp(fn, fPattern);
		    if numel(fMatches) > 0
			    fn
                found = 1;
			    break
		    end
        end
        if ~found
		    break
        end
        %

        dayFound = 1;
        fname = fn;
    
        M = readmatrix(dirname + "/" + fname, 'NumHeaderLines', 0);

        %if size(M,1) > 100
	%	M = readmatrix(dirname + "/" + fname, 'NumHeaderLines', 100);
        %end

        "loaded " + fname
    
        % Extract IDs and values
        cell_nums = M(:, 1) + 1;
	    if ~cell2mat(settings{"TEMP_FILE",1}) % neutral
            ids = M(:, 2:2:end);  % Extract all odd columns (IDs)
            values = M(:, 3:2:end); % Extract all even columns (values)
            temps_flat = [];
        else % selective
		    ids = M(:, 2:3:end);  % Extract all odd columns (IDs)
            values = M(:, 3:3:end); % Extract all even columns (values)
            temps = M(:, 4:3:end); % Extract all even columns (values)
            temps_flat = temps(:);
        end
        %

        ids_flat = ids(:);
        

        % Remove lineages with population of 0
	    if numel(temps_flat) > 0
        	temps_flat = temps_flat(~isnan(ids_flat));
	    end
        ids_flat = ids_flat(~isnan(ids_flat));    
        %

    
        %Combine IDs and associated topts into flat list (so far non
        %unique)
        if fnum == 1
            all_ids = ids_flat';
	        all_temps = temps_flat' ;
        else
            all_ids = [all_ids ids_flat'];
	        all_temps = [all_temps temps_flat'];
        end
        %
        
        %Add total data matrices to cell array of all data matrices
        all_vals{fnum} = values; %abundances
        all_idNames{fnum} = ids; %lineage ids
        all_cellNums{fnum} = cell_nums; %locations
        %
    end
    %%%%%%%%%%%

    results = {};

    
    if ~dayFound
        disp("WARNING: no data found for day " + num2str(day))
        results.V = [];
        results.origins = [];
        return
    end
    %%%%%%%%%%%% COMBINE INTO FINAL MATRIX / TEMP LIST / LIN LIST / ORIGIN LIST %%%%%%%%%%%%%%%%
    
    % Get unique list of 
    output = [];
    [a, b] = unique(all_ids) ;
    if numel(all_temps) > 0	
    	all_temps = all_temps(b);
    end
    all_ids = a;
    num_cols = numel(all_ids);
    %

    for fnum = 1:numel(all_vals) %for every file
        fnum
    
        cur_vals = all_vals{fnum};
        cur_idNames = all_idNames{fnum};
        cur_cellNums = all_cellNums{fnum};
        
        % Map IDs to column indices - what is the index column?
        [~, col_indices] = ismember(cur_idNames, all_ids);
        
        % Prepare data for sparse matrix
        col_flat = col_indices(:);
    
        cell_flat = repmat(cur_cellNums, size(col_indices, 2), 1 )';
        cell_flat = cell_flat(:);
    
        cell_flat = cell_flat(find(col_flat));
        col_flat = col_flat(find(col_flat));
    
        vals_flat = cur_vals(:);
        vals_flat = vals_flat(~isnan(vals_flat));

   


        disp(size(cell_flat));
        disp(size(col_flat));
        disp(size(vals_flat));
        disp(size(num_cells));
        disp(size(num_cols));


        S = sparse(cell_flat , col_flat  , vals_flat , num_cells, num_cols);
    
        if fnum == 1 
    	    output = S;
        else
	    output = output + S;
        end
    
    end
    

    

   %%%%%%%%%%%% GET LINEAGE ORIGINS %%%%%%%%%%%%%%%%

    
    %Get cell origins
    origI = 1;
    origins = ones(1, numel(all_ids));
    
    for i = 1:numel(all_ids)
	    while all_ids(i) >= limits(origI)
		    origI = origI + 1;
	    end
	    origins(i) = origI;
    end


    results.V = output;
    results.linIDs = all_ids;
    results.origins = origins;
    results.settings = settings;

    if numel(all_temps) > 0	
    	results.topt = all_temps;
    	results.tenv = temps;
    else
	    results.topt = [];
    	results.tenv = [];
    end
end

